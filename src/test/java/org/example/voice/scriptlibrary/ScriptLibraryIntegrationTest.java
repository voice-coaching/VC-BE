package org.example.voice.scriptlibrary;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.application.CustomContentService;
import org.example.voice.practicecontent.domain.CustomContentException;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.scriptlibrary.application.ScriptLibraryService;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.mypage.application.MyPageService;
import org.example.voice.user.application.UserService;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
class ScriptLibraryIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class CacheConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        org.springframework.cache.CacheManager libraryTestCache(){return new org.springframework.cache.support.NoOpCacheManager();}
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ScriptLibraryService library;
    @Autowired CustomContentService custom;
    @Autowired TrainingSessionService sessions;
    @Autowired MyPageService history;
    @Autowired UserService users;
    @Autowired MockMvc mvc;
    TransactionTemplate tx;
    Long owner,other;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);tx.executeWithoutResult(s->{
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        var a=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","library owner",now);
        var b=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","other",now);
        em.persist(a);em.persist(b);owner=a.getId();other=b.getId();
    });}
    @Test void topicsAndOriginalsAreEncryptedAndSurviveLibraryReads(){
        var topic=library.createTopic(owner,"발표");
        var original=library.save(owner,null,input("나만의 원고",topic.id()),"one");
        var list=library.list(owner);
        assertThat(list.items()).hasSize(1);assertThat(list.items().getFirst()).isEqualTo(original);
        assertThat(list.assignments()).containsEntry(original.id().toString(),"발표");
        library.renameTopic(owner,topic.id(),"면접");
        assertThat(library.list(owner).assignments()).containsEntry(original.id().toString(),"면접");
        tx.executeWithoutResult(s->{
            String raw=(String)em.createNativeQuery("select name_ciphertext from script_library_topics where id=:id",String.class).setParameter("id",topic.id()).getSingleResult();
            assertThat(raw).startsWith("v1:").doesNotContain("면접");
            Object[] row=(Object[])em.createNativeQuery("select script_text,custom_script_ciphertext from practice_contents where id=:id",Object[].class).setParameter("id",original.id()).getSingleResult();
            assertThat(row[0]).isEqualTo("[private]");assertThat(row[1].toString()).doesNotContain("나만의 원고");
        });
        assertThat(library.list(other).items()).isEmpty();
    }
    @Test void immutableEditsAndLibraryDeletionPreserveExistingSessionSources(){
        var old=library.save(owner,null,input("수정 전 원고",null),"old");
        var session=sessions.create(new TrainingSessionCreateRequestDto(old.id(),null,LearningFocus.PRONUNCIATION),owner);
        var next=library.save(owner,old.id(),input("수정 후 원고",null),"edit");
        assertThat(next.id()).isNotEqualTo(old.id());
        assertThat(library.list(owner).items()).extracting(c->c.id()).containsExactly(next.id());
        assertThat(sessions.getSession(session.sessionId(),owner).content().scriptText()).isEqualTo("수정 전 원고");
        library.delete(owner,List.of(next.id()));
        assertThat(library.list(owner).items()).isEmpty();
        assertThat(sessions.getSession(session.sessionId(),owner).content().scriptText()).isEqualTo("수정 전 원고");
        assertCode(()->custom.find(next.id(),owner),"RESOURCE_NOT_FOUND");
    }
    @Test void deletingLastHistoryKeepsSavedLibraryContentAndUnusedDeletionErasesText(){
        var saved=library.save(owner,null,input("보관할 원고",null),"saved");
        var session=sessions.create(new TrainingSessionCreateRequestDto(saved.id(),null,LearningFocus.PRONUNCIATION),owner);
        history.deleteHistory(owner,session.sessionId());
        assertThat(custom.find(saved.id(),owner)).isPresent();
        assertThat(library.list(owner).items()).hasSize(1);
        library.delete(owner,List.of(saved.id()));
        assertCode(()->custom.find(saved.id(),owner),"RESOURCE_NOT_FOUND");
        tx.executeWithoutResult(s->{var c=em.find(PracticeContent.class,saved.id());assertThat(c.getCustomScript()).isNull();assertThat(c.getCustomTitle()).isNull();});
    }
    @Test void ownerChecksMakeMixedOwnerBulkDeletionAtomic(){
        var mine=library.save(owner,null,input("내 문장",null),"mine");
        var theirs=library.save(other,null,input("다른 문장",null),"theirs");
        var foreignTopic=library.createTopic(other,"발표");
        assertCode(()->library.save(owner,theirs.id(),input("침범",null),"bad-edit"),"RESOURCE_NOT_FOUND");
        assertCode(()->library.save(owner,null,input("침범",foreignTopic.id()),"bad-topic"),"RESOURCE_NOT_FOUND");
        assertCode(()->library.delete(owner,List.of(mine.id(),theirs.id())),"RESOURCE_NOT_FOUND");
        assertCode(()->library.renameTopic(owner,foreignTopic.id(),"면접"),"RESOURCE_NOT_FOUND");
        assertCode(()->library.deleteTopic(owner,foreignTopic.id()),"RESOURCE_NOT_FOUND");
        assertThat(library.list(owner).items()).hasSize(1);assertThat(library.list(other).items()).hasSize(1);
    }
    @Test void idempotencySurvivesLostEditResponsesAndConcurrentSavesWithoutResurrection() throws Exception {
        var input=input("동시 원고",null);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->library.save(owner,null,input,"same"));
            var b=pool.submit(()->library.save(owner,null,input,"same"));
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        var old=library.list(owner).items().getFirst();
        var next=library.save(owner,old.id(),input("수정 원고",null),"edit");
        assertThat(library.save(owner,old.id(),input("수정 원고",null),"edit")).isEqualTo(next);
        assertCode(()->library.save(owner,old.id(),input("다른 원고",null),"edit"),"CONFLICT");
        library.delete(owner,List.of(next.id()));
        assertCode(()->library.save(owner,old.id(),input("수정 원고",null),"edit"),"RESOURCE_NOT_FOUND");
        assertThat(library.list(owner).items()).isEmpty();
    }
    @Test void topicDeletionRemovesOnlyItsLibraryEntriesAndRetainsHistory(){
        var topic=library.createTopic(owner,"발표");
        var grouped=library.save(owner,null,input("발표 문장",topic.id()),"grouped");
        var ungrouped=library.save(owner,null,input("미분류 문장",null),"ungrouped");
        var session=sessions.create(new TrainingSessionCreateRequestDto(grouped.id(),null,LearningFocus.PRONUNCIATION),owner);
        library.deleteTopic(owner,topic.id());
        assertThat(library.list(owner).topics()).isEmpty();
        assertThat(library.list(owner).items()).extracting(c->c.id()).containsExactly(ungrouped.id());
        assertThat(sessions.getSession(session.sessionId(),owner).content().scriptText()).isEqualTo("발표 문장");
    }
    @Test void validationAndWithdrawalsCannotLeakOrReviveOriginals(){
        assertCode(()->library.save(owner,null,input("😀".repeat(301),null),"bad"),"VALIDATION_ERROR");
        assertCode(()->library.save(owner,null,input("문장",null),null),"VALIDATION_ERROR");
        assertCode(()->library.createTopic(owner,"미분류"),"VALIDATION_ERROR");
        var topic=library.createTopic(owner,"발표");
        assertCode(()->library.createTopic(owner," 발표 "),"TOPIC_NAME_CONFLICT");
        var saved=library.save(owner,null,input("비공개 원고",topic.id()),"save");
        users.withdraw(owner);
        tx.executeWithoutResult(s->{
            assertThat(em.createQuery("select count(e) from ScriptLibraryEntry e where e.userId=:user",Long.class).setParameter("user",owner).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(t) from ScriptLibraryTopic t where t.userId=:user",Long.class).setParameter("user",owner).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(r) from ScriptLibraryRequest r where r.userId=:user",Long.class).setParameter("user",owner).getSingleResult()).isZero();
            assertThat(em.find(PracticeContent.class,saved.id()).getCustomScript()).isNull();
        });
        assertCode(()->library.list(owner),"FORBIDDEN");
    }
    @Test void authenticatedHttpContractProvidesLibraryAndTopicCrud() throws Exception {
        String base="/api/users/me/script-library";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(post(base+"/topics").with(auth(owner)).contentType("application/json").content("{\"name\":\"발표\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isNumber());
        Long topic=library.list(owner).topics().getFirst().id();
        String body="{\"title\":\"원고\",\"scriptText\":\"내 문장입니다.\",\"topicId\":"+topic+"}";
        mvc.perform(post(base).with(auth(owner)).header("Idempotency-Key","http").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.origin").value("USER_INPUT"));
        Long id=library.list(owner).items().getFirst().id();
        mvc.perform(get(base).with(auth(owner))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.assignments['"+id+"']").value("발표"));
        mvc.perform(put(base+"/"+id).with(auth(owner)).header("Idempotency-Key","edit-http").contentType("application/json").content(body.replace("내 문장입니다.","수정 문장입니다.")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scriptText").value("수정 문장입니다."));
        mvc.perform(put(base+"/topics/"+topic).with(auth(owner)).contentType("application/json").content("{\"name\":\"면접\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("면접"));
        Long next=library.list(owner).items().getFirst().id();
        mvc.perform(post(base+"/delete").with(auth(owner)).contentType("application/json").content("{\"ids\":["+next+"]}"))
                .andExpect(status().isOk());
        mvc.perform(delete(base+"/topics/"+topic).with(auth(owner))).andExpect(status().isOk());
        mvc.perform(post(base).with(auth(owner)).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    private ScriptLibraryService.Input input(String text,Long topic){return new ScriptLibraryService.Input("내 제목",text,topic);}
    private RequestPostProcessor auth(Long id){return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id),null,List.of()));}
    private void assertCode(Runnable run,String code){assertThatThrownBy(run::run).isInstanceOfSatisfying(CustomContentException.class,e->assertThat(e.getMessage()).isEqualTo(code));}
}
