package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.admin.application.AdminCourseService;
import org.example.voice.admin.domain.model.AdminCourseData.*;
import org.example.voice.auth.application.TokenService;
import org.example.voice.common.exception.BaseException;
import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practiceexample.application.PracticeExampleService;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AdminCourseIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired AdminCourseService service;
    @Autowired PracticeExampleService examples;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean org.springframework.cache.CacheManager caches;
    TransactionTemplate tx;
    long actor,member,other;
    String adminToken,memberToken;
    @BeforeEach void setup() {
        var local=new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(caches.getCache(anyString())).thenAnswer(call->local.getCache(call.getArgument(0)));
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{
            var admin=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","관리자",OffsetDateTime.now());
            ReflectionTestUtils.setField(admin,"role",UserRole.ADMIN);em.persist(admin);actor=admin.getId();
            var user=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","회원",OffsetDateTime.now());em.persist(user);member=user.getId();
            var another=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","새 회원",OffsetDateTime.now());em.persist(another);other=another.getId();
            adminToken=tokens.issueSession(admin).accessToken();memberToken=tokens.issueSession(user).accessToken();
        });
    }
    private Draft draft(List<Sentence> sentences){return new Draft(CourseType.INTONATION,"억양 클래스 "+actor,"설명",Difficulty.INTERMEDIATE,PublishStatus.PUBLISHED,5,sentences);}
    private Sentence sentence(int index){return new Sentence(null,null,"문장 "+index,"원문 "+index,"안내",LearningFocus.INTONATION,15);}
    private View create(){return service.create(actor,draft(java.util.stream.IntStream.rangeClosed(1,5).mapToObj(this::sentence).toList()),"클래스 등록");}

    @Test void requiresAdministratorAndReturnsNoStoreWithOneClassRow() throws Exception {
        var saved=create();
        mvc.perform(get("/api/admin/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/courses").header("Authorization","Bearer "+memberToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/courses").param("keyword","억양 클래스 "+actor).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.items[0].sentences.length()").value(5));
        mvc.perform(get("/api/admin/courses/"+saved.id()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
    }
    @Test void editsAndDeletesWithinNewRevisionWhilePinnedLearnersKeepTheirOriginalSentences() {
        var initial=create();
        tx.executeWithoutResult(status->{
            var set=em.createQuery("select s from PracticeExampleSet s where s.stepId in (select x.id from CourseStep x where x.course.id=:course) order by s.revision desc",org.example.voice.practiceexample.domain.entity.PracticeExampleSet.class).setParameter("course",initial.id()).setMaxResults(1).getSingleResult();
            var session=BeanUtils.instantiateClass(TrainingSession.class);
            ReflectionTestUtils.setField(session,"userId",member);ReflectionTestUtils.setField(session,"courseStepId",set.getStepId());
            ReflectionTestUtils.setField(session,"practiceExampleSetId",set.getId());ReflectionTestUtils.setField(session,"status",TrainingSessionStatus.RECORDING);em.persist(session);
        });
        var updated=new ArrayList<>(initial.sentences().subList(0,4));var first=updated.getFirst();
        updated.set(0,new Sentence(first.contentId(),first.contentVersion(),first.title(),"새 원문",first.hint(),first.focus(),first.estimatedSeconds()));
        var saved=service.edit(actor,initial.id(),initial.version(),draft(updated),"첫 문장 수정, 다섯째 삭제");
        assertThat(saved.version()).isEqualTo(initial.version()+1);assertThat(saved.sentences()).hasSize(4);
        assertThat(saved.sentences().getFirst().text()).isEqualTo("새 원문");
        assertThat(saved.sentences().stream().map(Sentence::contentId)).doesNotContainAnyElementsOf(initial.sentences().stream().map(Sentence::contentId).toList());
        tx.executeWithoutResult(status->assertThat(em.find(PracticeContent.class,first.contentId()).getScriptText()).isEqualTo("원문 1"));
        assertThat(examples.listForCourse(member,initial.id(),null).items()).hasSize(5);
        assertThat(examples.listForCourse(other,initial.id(),null).items()).hasSize(4);
        assertThat(examples.listForCourse(other,initial.id(),null).items().getFirst().text()).isEqualTo("새 원문");
    }
    @Test void rejectsStaleRevisionAndForeignOrDuplicatedSentenceIds() {
        var first=create();var second=create();
        var edited=service.edit(actor,first.id(),first.version(),draft(first.sentences()),"변경");
        assertThatThrownBy(()->service.edit(actor,first.id(),first.version(),draft(first.sentences()),"오래된 변경")).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->service.edit(actor,first.id(),edited.version(),draft(second.sentences()),"다른 클래스")).isInstanceOf(BaseException.class);
        var duplicated=List.of(edited.sentences().getFirst(),edited.sentences().getFirst());
        assertThatThrownBy(()->service.edit(actor,first.id(),edited.version(),draft(duplicated),"중복")).isInstanceOf(BaseException.class);
        assertThat(service.read(actor,first.id()).version()).isEqualTo(edited.version());
    }
    @Test void rejectsEmptyClassAndMissingReasonWithoutSaving() {
        assertThatThrownBy(()->service.create(actor,draft(List.of()),"빈 클래스")).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->service.create(actor,draft(List.of(sentence(1)))," ")).isInstanceOf(BaseException.class);
    }
    @Test void existingContentListCanExcludeIndividualClassRows() throws Exception {
        create();
        mvc.perform(get("/api/admin/contents").param("type","CLASS_PRACTICE").param("excludeClassPractice","true")
                .header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }
    @Test void concurrentEditsFromTheSameRevisionCommitOnlyOneReplacement() throws Exception {
        var initial=create();
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> edit=()->{
                start.await();
                try { service.edit(actor,initial.id(),initial.version(),draft(initial.sentences()),"동시 수정");return true; }
                catch(BaseException conflict){return false;}
            };
            var first=workers.submit(edit);var second=workers.submit(edit);start.countDown();
            assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(service.read(actor,initial.id()).version()).isEqualTo(initial.version()+1);
    }
    @Test void httpCreateAndEditMapDtoAndRejectReplayingAStaleRevision() throws Exception {
        var body=json.writeValueAsString(Map.of("course",draft(java.util.stream.IntStream.rangeClosed(1,5).mapToObj(this::sentence).toList()),"reason","클래스 등록"));
        var result=mvc.perform(post("/api/admin/courses").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
        var current=service.read(actor,id);
        var edit=json.writeValueAsString(Map.of("version",current.version(),"course",draft(current.sentences().subList(0,4)),"reason","문장 하나 삭제"));
        mvc.perform(patch("/api/admin/courses/"+id).header("Authorization","Bearer "+adminToken).contentType("application/json").content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.sentences.length()").value(4));
        mvc.perform(patch("/api/admin/courses/"+id).header("Authorization","Bearer "+adminToken).contentType("application/json").content(edit))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_EDIT_CONFLICT"));
    }
    @Test void draftClassDoesNotPublishItsNewSentenceContents() {
        var input=draft(List.of(sentence(1)));
        var draft=new Draft(input.courseType(),input.title(),input.description(),input.difficulty(),PublishStatus.DRAFT,input.estimatedMinutes(),input.sentences());
        var saved=service.create(actor,draft,"초안 등록");
        tx.executeWithoutResult(status->assertThat(em.find(PracticeContent.class,saved.sentences().getFirst().contentId()).getStatus()).isEqualTo(PublishStatus.DRAFT));
        var published=new Draft(input.courseType(),input.title(),input.description(),input.difficulty(),PublishStatus.PUBLISHED,input.estimatedMinutes(),saved.sentences());
        var result=service.edit(actor,saved.id(),saved.version(),published,"검수 후 공개");
        tx.executeWithoutResult(status->assertThat(em.find(PracticeContent.class,result.sentences().getFirst().contentId()).isPublished()).isTrue());
    }
}
