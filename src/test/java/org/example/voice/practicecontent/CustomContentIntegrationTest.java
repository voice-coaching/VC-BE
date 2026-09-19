package org.example.voice.practicecontent;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.application.CustomContentService;
import org.example.voice.practicecontent.application.PracticeContentService;
import org.example.voice.practicecontent.domain.CustomContentException;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.application.UserService;
import org.example.voice.mypage.application.MyPageService;
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
class CustomContentIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class CacheConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        org.springframework.cache.CacheManager customTestCache(){return new org.springframework.cache.support.NoOpCacheManager();}
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired CustomContentService custom;
    @Autowired PracticeContentService contentService;
    @Autowired TrainingSessionService sessions;
    @Autowired MyPageService history;
    @Autowired UserService users;
    @Autowired MockMvc mvc;
    @Autowired org.example.voice.training.domain.port.VoiceRecordingReader recordings;
    TransactionTemplate tx;
    Long owner,other;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);tx.executeWithoutResult(s->{
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        var a=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","custom owner",now);
        var b=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","other",now);
        em.persist(a);em.persist(b);owner=a.getId();other=b.getId();
    });}
    @Test void encryptsAtRestAndKeepsStableNumericIdAndUnicodeSentences(){
        var data=custom.create(owner,input("  비밀 연설 원고입니다. 다음 문장입니다.  "),"create");
        assertThat(data.id()).isPositive();assertThat(data.scriptText()).isEqualTo("비밀 연설 원고입니다. 다음 문장입니다.");
        assertThat(custom.create(owner,input("비밀 연설 원고입니다. 다음 문장입니다."),"create")).isEqualTo(data);
        assertThat(contentService.getPracticeContent(data.id(),owner).scriptText()).isEqualTo(data.scriptText());
        tx.executeWithoutResult(s->{
            Object[] row=(Object[])em.createNativeQuery("select script_text,title,custom_script_ciphertext,custom_title_ciphertext from practice_contents where id=:id",Object[].class)
                    .setParameter("id",data.id()).getSingleResult();
            assertThat(row[0]).isEqualTo("[private]");assertThat(row[1]).isEqualTo("내 문장");
            assertThat(row[2].toString()).startsWith("v1:").doesNotContain(data.scriptText());
            assertThat(row[3].toString()).startsWith("v1:").doesNotContain("내 제목");
        });
        var emoji=custom.create(owner,input("😀".repeat(300)),null);
        assertThat(emoji.sentences().getFirst().endOffset()).isEqualTo(300);
        assertCode(()->custom.create(owner,input("😀".repeat(301)),null),"VALIDATION_ERROR");
        for(String value:List.of("", "　 ", "문장\u0000", "문장\n다음")) assertCode(()->custom.create(owner,input(value),null),"VALIDATION_ERROR");
    }
    @Test void idempotencyConflictAndConcurrentRequests() throws Exception {
        var first=custom.create(owner,input("첫 원고"),"same");
        assertCode(()->custom.create(owner,input("다른 원고"),"same"),"CONFLICT");
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->custom.create(owner,input("동시 원고"),"parallel"));
            var b=pool.submit(()->custom.create(owner,input("동시 원고"),"parallel"));
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        assertThat(custom.create(other,input("첫 원고"),"same").id()).isNotEqualTo(first.id());
    }
    @Test void onlyOwnerCanReadAndUseCustomContentAndPublicListExcludesIt() throws Exception {
        var data=custom.create(owner,input("내 원고"),null);
        assertCode(()->contentService.getPracticeContent(data.id(),other),"RESOURCE_NOT_FOUND");
        assertCode(()->sessions.create(new TrainingSessionCreateRequestDto(data.id(),null,LearningFocus.BOTH),other),"RESOURCE_NOT_FOUND");
        assertCode(()->sessions.create(new TrainingSessionCreateRequestDto(data.id(),1L,LearningFocus.BOTH),owner),"CONFLICT");
        mvc.perform(get("/api/practice-contents").param("type","SENTENCE").param("category","CUSTOM").with(auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        var session=sessions.create(new TrainingSessionCreateRequestDto(data.id(),null,LearningFocus.BOTH),owner);
        assertThat(sessions.getSession(session.sessionId(),owner).content().scriptText()).isEqualTo("내 원고");
    }
    @Test void historyDeletionErasesOnlyLastReferenceAndDoesNotRecreateOnReplay(){
        var data=custom.create(owner,input("지울 원고"),"history");
        var one=sessions.create(new TrainingSessionCreateRequestDto(data.id(),null,LearningFocus.BOTH),owner);
        var two=sessions.create(new TrainingSessionCreateRequestDto(data.id(),null,LearningFocus.BOTH),owner);
        history.deleteHistory(owner,one.sessionId()); assertThat(custom.find(data.id(),owner)).isPresent();
        history.deleteHistory(owner,two.sessionId());
        assertCode(()->custom.find(data.id(),owner),"RESOURCE_NOT_FOUND");
        assertCode(()->custom.create(owner,input("지울 원고"),"history"),"RESOURCE_NOT_FOUND");
        tx.executeWithoutResult(s->{var c=em.find(PracticeContent.class,data.id());assertThat(c.getCustomScript()).isNull();assertThat(c.getCustomTitle()).isNull();assertThat(c.getCustomDeletedAt()).isNotNull();});
    }
    @Test void withdrawalErasesUnusedAndUsedOriginals(){
        var unused=custom.create(owner,input("미사용 원고"),null);
        var used=custom.create(owner,input("사용 원고"),null);
        sessions.create(new TrainingSessionCreateRequestDto(used.id(),null,LearningFocus.BOTH),owner);
        users.withdraw(owner);
        tx.executeWithoutResult(s->{assertThat(em.find(PracticeContent.class,unused.id()).getCustomScript()).isNull();assertThat(em.find(PracticeContent.class,used.id()).getCustomScript()).isNull();});
        assertCode(()->custom.create(owner,input("새 원고"),null),"FORBIDDEN");
    }
    @Test void completedHistoryUsesRealTitleAndDetailUsesOriginalScript(){
        var data=custom.create(owner,input("기록에 보일 원고"),null);
        var session=sessions.create(new TrainingSessionCreateRequestDto(data.id(),null,LearningFocus.BOTH),owner);
        Long outboxId=tx.execute(s->{
            var v=em.find(TrainingSession.class,session.sessionId());v.startAnalysis();v.complete(10);
            var r=org.example.voice.training.domain.entity.VoiceRecording.create(v,1,"synthetic-audio","audio/wav",100L,1000,"a".repeat(64),org.example.voice.training.domain.type.RecordingQualityStatus.PASS,null,null);
            org.springframework.test.util.ReflectionTestUtils.setField(r,"selected",true);em.persist(r);
            var a=org.example.voice.analysis.domain.entity.AnalysisResult.pending(r,UUID.randomUUID());
            org.springframework.test.util.ReflectionTestUtils.setField(a,"status",org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED);
            org.springframework.test.util.ReflectionTestUtils.setField(a,"overallScore",java.math.BigDecimal.valueOf(80));
            em.persist(a);
            var outbox=org.example.voice.analysis.domain.entity.AnalysisRequestOutbox.pending(UUID.randomUUID(),a,"{\"scriptText\":\"기록에 보일 원고\"}");
            em.persist(outbox);return outbox.getId();
        });
        var page=history.getHistory(owner,null,null,LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),0,20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().title()).isEqualTo("내 제목");
        assertThat(history.getHistoryDetail(owner,session.sessionId()).content().scriptText()).isEqualTo(data.scriptText());
        assertThat(history.getHistoryDetail(owner,session.sessionId()).analysis().id()).isPositive();
        assertThat(recordings.findSelectedForAnalysis(session.sessionId(),owner).orElseThrow().scriptText()).isEqualTo(data.scriptText());
        tx.executeWithoutResult(s->{
            Object[] raw=(Object[])em.createNativeQuery("select payload,private_payload_ciphertext from analysis_request_outbox where id=:id",Object[].class).setParameter("id",outboxId).getSingleResult();
            assertThat(raw[0]).isEqualTo("{}");assertThat(raw[1].toString()).startsWith("v1:").doesNotContain(data.scriptText());
            assertThat(em.find(org.example.voice.analysis.domain.entity.AnalysisRequestOutbox.class,outboxId).getPayload()).contains(data.scriptText());
        });
        history.deleteHistory(owner,session.sessionId());
        assertCode(()->custom.find(data.id(),owner),"RESOURCE_NOT_FOUND");
    }
    @Test void httpContractAndValidation() throws Exception {
        String body="{\"title\":\"내 제목\",\"scriptText\":\"테스트 원고\",\"learningFocus\":\"BOTH\",\"retention\":\"SESSION_HISTORY\",\"locale\":\"ko-KR\"}";
        mvc.perform(post("/api/practice-contents/custom").with(auth(owner)).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isNumber()).andExpect(jsonPath("$.data.origin").value("USER_INPUT"))
                .andExpect(jsonPath("$.data.sentences[0].text").value("테스트 원고"));
        mvc.perform(post("/api/practice-contents/custom").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/practice-contents/custom").with(auth(owner)).contentType("application/json").content(body.replace("ko-KR","en-US")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/practice-contents/custom").with(auth(owner)).contentType("application/json").content(body.replace("BOTH","INVALID")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    private CustomContentService.Input input(String script){return new CustomContentService.Input("내 제목",script,LearningFocus.BOTH,"SESSION_HISTORY","ko-KR");}
    private RequestPostProcessor auth(Long id){return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id),null,List.of()));}
    private void assertCode(Runnable run,String code){assertThatThrownBy(run::run).isInstanceOfSatisfying(CustomContentException.class,e->assertThat(e.getMessage()).isEqualTo(code));}
}
