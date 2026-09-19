package org.example.voice.title;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.title.application.TitleService;
import org.example.voice.title.domain.TitleException;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.TitlePolicy;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.domain.entity.*;
import org.example.voice.training.domain.type.*;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TitleIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired TitleService titles;
    @Autowired TrainingSessionService sessions;
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.cache.CacheManager cacheManager;
    Long user, other, content;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        var localCaches = new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        org.mockito.Mockito.when(cacheManager.getCache(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> localCaches.getCache(call.getArgument(0)));
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            var now = OffsetDateTime.now(ZoneOffset.UTC);
            var owner = User.createLocal(UUID.randomUUID()+"@example.invalid","hash","title user",now);
            var outsider = User.createLocal(UUID.randomUUID()+"@example.invalid","hash","title other",now);
            em.persist(owner); em.persist(outsider); user=owner.getId(); other=outsider.getId();
            PracticeContent c = construct(PracticeContent.class);
            Map<String,Object> values = Map.of("contentType",ContentType.SENTENCE,"learningFocus",LearningFocus.BOTH,
                    "title","시험 문장", "scriptText","정확한 발음으로 읽습니다.", "difficulty",Difficulty.BEGINNER,
                    "status",PublishStatus.PUBLISHED,"createdAt",now,"updatedAt",now);
            values.forEach((key,value)->ReflectionTestUtils.setField(c,key,value)); em.persist(c); content=c.getId();
            int[] counts={5,15,30,60}, scores={70,75,80,85};
            for(int i=1;i<TitleRank.values().length;i++) {
                var rank=TitleRank.values()[i]; var rule=em.find(TitlePolicy.class,rank);
                if(rule==null) { rule=construct(TitlePolicy.class); ReflectionTestUtils.setField(rule,"targetRank",rank); }
                ReflectionTestUtils.setField(rule,"requiredTrainingCount",(long)counts[i-1]);
                ReflectionTestUtils.setField(rule,"passingScore",scores[i-1]); ReflectionTestUtils.setField(rule,"practiceContentId",content);
                if(!em.contains(rule)) em.persist(rule);
            }
        });
    }
    @Test void countsOnlyOpenEligibilityAndDeduplicatesExamAndSession() {
        completed(4); assertThat(titles.progress(user).next().eligible()).isFalse();
        assertCode(() -> titles.create(user,null),"TITLE_EXAM_NOT_ELIGIBLE");
        completed(1); var before=titles.progress(user);
        assertThat(before.code()).isEqualTo("ABSOLUTE_BEGINNER"); assertThat(before.next().eligible()).isTrue();
        var exam=titles.create(user,"one"); assertThat(titles.create(user,"two").id()).isEqualTo(exam.id());
        assertThat(titles.create(user,"one")).isEqualTo(exam);
        var request=new TrainingSessionCreateRequestDto(content,null,LearningFocus.BOTH,exam.id());
        var first=sessions.create(request,user); assertThat(sessions.create(request,user).sessionId()).isEqualTo(first.sessionId());
        assertCode(() -> titles.createSession(user,exam.id(),content+999,null,LearningFocus.BOTH),"TITLE_EXAM_CONTENT_MISMATCH");
        assertCode(() -> titles.get(other,exam.id()),"RESOURCE_NOT_FOUND");
    }
    @Test void failedThenPassingExamUsesServerScoreAndStablePolicySnapshot() {
        completed(5); var exam=titles.create(user,"first");
        Long session=bind(exam.id()); Long failed=analysis(user,session,new BigDecimal("69.99"));
        var grade=titles.submit(user,exam.id(),failed); assertThat(grade.passed()).isFalse();
        assertThat(titles.progress(user).code()).isEqualTo("ABSOLUTE_BEGINNER");
        var retry=titles.create(user,"retry"); Long second=bind(retry.id()); Long pass=analysis(user,second,new BigDecimal("70.00"));
        tx.executeWithoutResult(s -> ReflectionTestUtils.setField(em.find(TitlePolicy.class,TitleRank.BEGINNER),"passingScore",99));
        var passed=titles.submit(user,retry.id(),pass); assertThat(passed.passed()).isTrue();
        assertThat(passed.passingScore()).isEqualTo(70); assertThat(titles.progress(user).code()).isEqualTo("BEGINNER");
        assertThat(titles.submit(user,retry.id(),pass)).isEqualTo(passed);
        assertCode(() -> titles.submit(user,retry.id(),failed),"TITLE_EXAM_ALREADY_GRADED");
        assertThat(titles.create(user,"retry")).isEqualTo(retry);
        assertThat(titles.create(user,"first")).isEqualTo(exam);
    }
    @Test void simultaneousCreationAndSubmissionDoNotDuplicatePromotion() throws Exception {
        completed(5);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->titles.create(user,"a")); var b=pool.submit(()->titles.create(user,"b"));
            var exam=a.get(10,TimeUnit.SECONDS); assertThat(b.get(10,TimeUnit.SECONDS).id()).isEqualTo(exam.id());
            Long result=analysis(user,bind(exam.id()),BigDecimal.valueOf(90));
            var first=pool.submit(()->titles.submit(user,exam.id(),result)); var second=pool.submit(()->titles.submit(user,exam.id(),result));
            assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
            assertThat(titles.progress(user).code()).isEqualTo("BEGINNER");
        }
    }
    @Test void blocksOtherUsersAndUnrelatedOrUnreadyAnalysis() {
        completed(5); var exam=titles.create(user,null); Long session=bind(exam.id());
        var alienSession=sessions.create(new TrainingSessionCreateRequestDto(content,null,LearningFocus.BOTH),other);
        Long alien=analysis(other,alienSession.sessionId(),BigDecimal.valueOf(100));
        assertCode(()->titles.submit(user,exam.id(),alien),"RESOURCE_NOT_FOUND");
        var unrelated=sessions.create(new TrainingSessionCreateRequestDto(content,null,LearningFocus.BOTH),user);
        Long result=analysis(user,unrelated.sessionId(),BigDecimal.valueOf(100));
        assertCode(()->titles.submit(user,exam.id(),result),"ANALYSIS_NOT_COMPLETED");
        Long unready=analysis(user,session,BigDecimal.valueOf(100));
        tx.executeWithoutResult(s->ReflectionTestUtils.setField(em.find(AnalysisResult.class,unready),"status",AnalysisStatus.PROCESSING));
        assertCode(()->titles.submit(user,exam.id(),unready),"ANALYSIS_NOT_COMPLETED");
    }
    @Test void coachingWithheldScoreDoesNotGradeOrPromote() throws Exception {
        completed(5); var exam = titles.create(user, null); Long session = bind(exam.id());
        Long id = analysis(user, session, BigDecimal.valueOf(99));
        tx.executeWithoutResult(s -> {
            try {
                var document = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        org.example.voice.analysis.infrastructure.runpod.CoachingContractTest.coaching(),
                        org.example.voice.analysis.domain.model.AnalysisCoaching.class);
                em.find(AnalysisResult.class, id).applyCoaching(document);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        });
        assertCode(() -> titles.submit(user, exam.id(), id), "ANALYSIS_SCORE_UNAVAILABLE");
        mvc.perform(get("/api/analyses/{id}", id).with(as(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coaching.schemaVersion").value("voice-coaching.coaching-result.v1"))
                .andExpect(jsonPath("$.data.coaching.items[0].candidateId").value("candidate-0"))
                .andExpect(jsonPath("$.data.overallScore").doesNotExist());
        mvc.perform(get("/api/analyses/{id}", id).with(as(other))).andExpect(status().isNotFound());
        assertThat(titles.progress(user).code()).isEqualTo(TitleRank.values()[0].name());
        tx.executeWithoutResult(s -> {
            var stored = em.find(org.example.voice.title.domain.entity.TitleExam.class, exam.id());
            assertThat(stored.graded()).isFalse();
        });
    }
    @Test void httpContractIncludesAuthenticationErrorsAndLinkedSession() throws Exception {
        mvc.perform(get("/api/users/me/title")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/title").with(as(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.next.requiredTrainingCount").value(5));
        mvc.perform(post("/api/users/me/title-exams").with(as(user))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TITLE_EXAM_NOT_ELIGIBLE"));
        completed(5); var exam=titles.create(user,null);
        mvc.perform(get("/api/users/me/title-exams/{id}",exam.id()).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(post("/api/training-sessions").with(as(user)).contentType("application/json")
                        .content("{\"contentId\":"+content+",\"learningFocus\":\"BOTH\",\"titleExamId\":"+exam.id()+"}"))
                .andExpect(status().isOk());
    }
    @Test void progressesOneLevelAtEachThresholdAndStopsAtMaximum() {
        int[] added={5,10,15,30}, passing={70,75,80,85};
        for(int i=0;i<4;i++) {
            completed(added[i]); var exam=titles.create(user,null);
            Long result=analysis(user,bind(exam.id()),BigDecimal.valueOf(passing[i]));
            assertThat(titles.submit(user,exam.id(),result).passed()).isTrue();
            assertThat(titles.progress(user).code()).isEqualTo(TitleRank.values()[i+1].name());
        }
        assertThat(titles.progress(user).next()).isNull();
        assertCode(()->titles.create(user,null),"MAX_TITLE_REACHED");
    }
    @Test void missingPublishedPolicyContentReturnsExplicitUnavailable() {
        completed(5);
        tx.executeWithoutResult(s->ReflectionTestUtils.setField(em.find(TitlePolicy.class,TitleRank.BEGINNER),"practiceContentId",null));
        assertCode(()->titles.create(user,null),"TITLE_EXAM_CONTENT_UNAVAILABLE");
    }
    private Long bind(Long exam) { return sessions.create(new TrainingSessionCreateRequestDto(content,null,LearningFocus.BOTH,exam),user).sessionId(); }
    private void completed(int count) { tx.executeWithoutResult(s->{ for(int i=0;i<count;i++){var t=TrainingSession.create(user,em.find(PracticeContent.class,content),null,LearningFocus.BOTH); t.startAnalysis(); t.complete(20); em.persist(t);}}); }
    private Long analysis(Long owner,Long session,BigDecimal score) {
        return tx.execute(s->{ var t=em.find(TrainingSession.class,session); t.startAnalysis();
            var recording=VoiceRecording.create(t,1,"test-recording","audio/wav",100L,5000,"a".repeat(64),RecordingQualityStatus.PASS,BigDecimal.ONE,BigDecimal.ONE);
            recording.select(); em.persist(recording); var a=AnalysisResult.pending(recording,UUID.randomUUID());
            ReflectionTestUtils.setField(a,"status",AnalysisStatus.COMPLETED); ReflectionTestUtils.setField(a,"overallScore",score); em.persist(a); em.flush(); return a.getId(); });
    }
    private static <T> T construct(Class<T> type) { try {var c=type.getDeclaredConstructor(); c.setAccessible(true); return c.newInstance();} catch(Exception e){throw new AssertionError(e);} }
    private void assertCode(Runnable action,String code) { assertThatThrownBy(action::run).isInstanceOf(TitleException.class).hasMessage(code); }
    private RequestPostProcessor as(Long id) {return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id),null,List.of()));}
}
