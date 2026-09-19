package org.example.voice.course;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.course.application.CourseEducationService;
import org.example.voice.course.domain.CourseEducationException;
import org.example.voice.course.domain.entity.*;
import org.example.voice.course.domain.type.*;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CourseEducationIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class CacheTestConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        org.springframework.cache.CacheManager educationTestCacheManager() {
            return new org.springframework.cache.support.NoOpCacheManager();
        }
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired CourseEducationService education;
    @Autowired TrainingSessionService sessions;
    @Autowired MockMvc mvc;
    TransactionTemplate tx;
    Long owner, other, course, step, content;
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            var now = OffsetDateTime.now(ZoneOffset.UTC);
            var u = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "course test", now);
            var o = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "other", now);
            em.persist(u); em.persist(o); owner=u.getId(); other=o.getId();
            var c = construct(PracticeContent.class);
            fields(c, Map.of("contentType", ContentType.SENTENCE,"learningFocus",LearningFocus.BOTH,
                    "title","실제 테스트 원고","scriptText","발음을 연습합니다.","difficulty",Difficulty.BEGINNER,
                    "status",PublishStatus.PUBLISHED,"createdAt",now,"updatedAt",now)); em.persist(c); content=c.getId();
            var cr = construct(Course.class);
            fields(cr, Map.of("courseType",CourseType.PRONUNCIATION,"title","교육","difficulty",Difficulty.BEGINNER,
                    "status",PublishStatus.PUBLISHED,"createdAt",now,"updatedAt",now)); em.persist(cr); course=cr.getId();
            var st = construct(CourseStep.class);
            fields(st, Map.of("course",cr,"practiceContent",c,"stepType",CourseStepType.PRACTICE,"stepOrder",1,
                    "title","첫 단계","body","원래 교육 내용","required",true)); em.persist(st); step=st.getId();
            revision(1,"원래 교육 내용");
        });
    }
    @Test void detailAndListContractWithAuthenticationAndCourseOwnership() throws Exception {
        mvc.perform(get(path()).with(auth(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(step)).andExpect(jsonPath("$.data.contentRevision").value(1))
                .andExpect(jsonPath("$.data.blocks[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.blocks[0].body").value("원래 교육 내용"))
                .andExpect(jsonPath("$.data.completed").value(false));
        mvc.perform(get("/api/courses/"+course+"/steps").with(auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stepCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses/"+(course+999999)+"/steps/"+step).with(auth(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/courses/"+course+"/steps/0").with(auth(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/courses/"+course+"/steps/invalid").with(auth(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void preservesSessionRevisionAcrossUpdatesAndCompletedHistory() {
        var first = sessions.create(new TrainingSessionCreateRequestDto(content,step,LearningFocus.BOTH),owner);
        tx.executeWithoutResult(s -> revision(2,"새 교육 내용"));
        assertThat(education.detail(course,step,owner,null).contentRevision()).isEqualTo(1);
        assertThat(education.detail(course,step,other,null).contentRevision()).isEqualTo(2);
        var second = sessions.create(new TrainingSessionCreateRequestDto(content,step,LearningFocus.BOTH),owner);
        assertThat(education.detail(course,step,owner,null).contentRevision()).isEqualTo(2);
        assertThat(education.detail(course,step,owner,first.sessionId()).blocks().getFirst().body()).isEqualTo("원래 교육 내용");
        tx.executeWithoutResult(s -> { var session=em.find(TrainingSession.class,first.sessionId()); session.startAnalysis(); session.complete(20); });
        assertThat(education.detail(course,step,owner,first.sessionId()).contentRevision()).isEqualTo(1);
        assertCode(() -> education.detail(course,step,other,second.sessionId()),"RESOURCE_NOT_FOUND");
    }
    @Test void startedAtZeroIsNotCompletedAndProgressIsUserSpecific() {
        tx.executeWithoutResult(s -> em.persist(UserCourseProgress.start(em.find(Course.class,course),owner,step,OffsetDateTime.now(ZoneOffset.UTC))));
        assertThat(education.detail(course,step,owner,null).completed()).isFalse();
        tx.executeWithoutResult(s -> em.createQuery("select p from UserCourseProgress p where p.userId=:u",UserCourseProgress.class)
                .setParameter("u",owner).getSingleResult().updateProgress(step,BigDecimal.valueOf(50),OffsetDateTime.now(ZoneOffset.UTC)));
        assertThat(education.detail(course,step,owner,null).completed()).isTrue();
        assertThat(education.detail(course,step,other,null).completed()).isFalse();
    }
    @Test void hidesUnpublishedCourseAndRejectsUnrelatedContent() {
        assertCode(() -> sessions.create(new TrainingSessionCreateRequestDto(content,step+999999,LearningFocus.BOTH),owner),"RESOURCE_NOT_FOUND");
        tx.executeWithoutResult(s -> ReflectionTestUtils.setField(em.find(CourseStep.class,step),"practiceContent",null));
        assertCode(() -> sessions.create(new TrainingSessionCreateRequestDto(content,step,LearningFocus.BOTH),owner),"COURSE_CONTENT_MISMATCH");
        tx.executeWithoutResult(s -> ReflectionTestUtils.setField(em.find(Course.class,course),"status",PublishStatus.HIDDEN));
        assertCode(() -> education.detail(course,step,owner,null),"RESOURCE_NOT_FOUND");
    }
    @Test void failsClosedOnUnpreparedOrUnsafeBlocks() {
        for(String blocks : List.of("[]","[{\"type\":\"HTML\",\"body\":\"test\"}]",
                "[{\"type\":\"TEXT\",\"body\":\"<script>alert(1)</script>\"}]",
                "[{\"type\":\"IMAGE\",\"assetUrl\":\"javascript:alert(1)\",\"altText\":\"x\",\"aspectRatio\":1}]",
                "[{\"type\":\"TEXT\",\"body\":\"valid\",\"assetUrl\":\"javascript:alert(1)\"}]")) {
            tx.executeWithoutResult(s -> rawRevision(nextRevision(), blocks));
            assertCode(() -> education.detail(course,step,owner,null),"COURSE_CONTENT_UNAVAILABLE");
        }
    }
    @Test void acceptsAllSupportedStructuredBlocks() {
        String blocks="""
                [{"type":"TEXT","title":"안내","body":"본문"},
                 {"type":"IMAGE","assetUrl":"https://cdn.example.invalid/diagram.png","altText":"설명","aspectRatio":1.5},
                 {"type":"DIAGRAM","diagram":{"kind":"TONGUE_POSITION_RIEUL","altText":"혀 위치"}},
                 {"type":"CHECKLIST","items":["첫 항목","둘째 항목"]},
                 {"type":"AUDIO","referenceAudioId":123},
                 {"type":"PRACTICE_PROMPT","practiceContentId":456}]
                """;
        tx.executeWithoutResult(s -> rawRevision(2,blocks));
        assertThat(education.detail(course,step,owner,null).blocks()).hasSize(6);
    }
    private int nextRevision() { return em.createQuery("select max(r.revision) from CourseStepRevision r where r.stepId=:s",Integer.class).setParameter("s",step).getSingleResult()+1; }
    private void revision(int n,String text) { rawRevision(n,"[{\"type\":\"TEXT\",\"body\":\""+text+"\"}]"); }
    private void rawRevision(int n,String blocks) {
        var r=construct(CourseStepRevision.class);
        fields(r,Map.of("stepId",step,"revision",n,"title","첫 단계","stepOrder",1,"stepType",CourseStepType.PRACTICE,"blocksJson",blocks));
        em.persist(r);
    }
    private String path(){ return "/api/courses/"+course+"/steps/"+step; }
    private RequestPostProcessor auth(Long id){return authentication(new UsernamePasswordAuthenticationToken(new LoginUser(id),null,List.of()));}
    private void assertCode(Runnable action,String code){assertThatThrownBy(action::run).isInstanceOfSatisfying(CourseEducationException.class,e->assertThat(e.getMessage()).isEqualTo(code));}
    private static void fields(Object target,Map<String,Object> values){values.forEach((k,v)->ReflectionTestUtils.setField(target,k,v));}
    private static <T> T construct(Class<T> type){try{var c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}catch(Exception e){throw new AssertionError(e);}}
}
