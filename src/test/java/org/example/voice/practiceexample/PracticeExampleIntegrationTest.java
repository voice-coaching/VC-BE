package org.example.voice.practiceexample;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.course.domain.entity.*;
import org.example.voice.course.domain.type.*;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practiceexample.application.*;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.entity.*;
import org.example.voice.practiceexample.domain.model.ExampleData.*;
import org.example.voice.practiceexample.domain.port.*;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.title.application.TitleService;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.*;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PracticeExampleIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PracticeExampleService examples;
    @Autowired ExampleAudioService audio;
    @Autowired ExampleAudioCache cache;
    @Autowired TrainingSessionService sessions;
    @Autowired TitleService titles;
    @Autowired MockMvc mvc;
    @MockitoBean ExampleSpeechSynthesizer synthesizer;
    TransactionTemplate tx;
    Long user, other, course, step;
    String firstId;
    Long firstContent;
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            var now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
            var u = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "example test", now); em.persist(u); user = u.getId();
            var o = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "other", now); em.persist(o); other = o.getId();
            var c = construct(Course.class); fields(c, Map.of("courseType", CourseType.PRONUNCIATION, "title", "테스트 교육",
                    "difficulty", Difficulty.BEGINNER, "status", PublishStatus.PUBLISHED, "createdAt", now, "updatedAt", now));
            em.persist(c); course = c.getId();
            var st = construct(CourseStep.class); fields(st, Map.of("course", c, "stepType", CourseStepType.PRACTICE,
                    "stepOrder", 1, "title", "예문 단계", "body", "단계 설명", "required", true)); em.persist(st); step = st.getId();
            if (em.find(ExampleTtsQuota.class, 0L) == null) em.persist(new ExampleTtsQuota(0L));
            seed(1);
        });
    }
    private void seed(int revision) {
        var now = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        var education = construct(CourseStepRevision.class);
        fields(education, Map.of("stepId", step, "revision", revision, "title", "교육 " + revision, "stepOrder", 1,
                "stepType", CourseStepType.PRACTICE, "blocksJson", "[{\"type\":\"TEXT\",\"body\":\"교육 설명\"}]")); em.persist(education);
        var set = construct(PracticeExampleSet.class);
        fields(set, Map.of("stepId", step, "revision", revision, "educationRevisionId", education.getId(), "publishedAt", now)); em.persist(set);
        for (int i = 1; i <= 5; i++) {
            var content = construct(PracticeContent.class);
            fields(content, Map.of("contentType", ContentType.CLASS_PRACTICE, "learningFocus", LearningFocus.BOTH,
                    "title", "개정 " + revision + " 예문 " + i, "scriptText", "테스트용 개정 " + revision + "의 문장 " + i + "입니다.",
                    "difficulty", Difficulty.BEGINNER, "status", PublishStatus.PUBLISHED, "createdAt", now, "updatedAt", now)); em.persist(content);
            var example = construct(PracticeExample.class); String id = "step-" + step + "-r" + revision + "-" + i;
            fields(example, Map.of("id", id, "set", set, "content", content, "order", i, "hint", "테스트 안내", "locale", "ko-KR")); em.persist(example);
            if (revision == 1 && i == 1) { firstId = id; firstContent = content.getId(); }
        }
    }
    @Test void returnsExactlyFiveOrderedItemsAndPinsSessionTextAndRevision() {
        var list = examples.list(user, course, step, null);
        assertThat(list.items()).hasSize(5); assertThat(list.items()).extracting(Item::order).containsExactly(1,2,3,4,5);
        assertThat(list.items().getFirst().practiceContentId()).isEqualTo(firstContent);
        var session = sessions.create(new TrainingSessionCreateRequestDto(firstContent, step, LearningFocus.BOTH), user);
        tx.executeWithoutResult(s -> seed(2));
        assertThat(examples.list(user, course, step, null).revision()).isEqualTo(1);
        assertThat(examples.list(other, course, step, null).revision()).isEqualTo(2);
        var detail = sessions.getSession(session.sessionId(), user);
        assertThat(detail.content().practiceExampleRevision()).isEqualTo(1);
        assertThat(detail.content().scriptText()).isEqualTo(list.items().getFirst().text());
        assertThat(examples.audioSource(user, firstId).text()).isEqualTo(detail.content().scriptText());
        assertStatus(404, () -> examples.list(other, course, step, session.sessionId()));
        tx.executeWithoutResult(s -> { var saved = em.find(TrainingSession.class, session.sessionId()); saved.startAnalysis(); saved.complete(10); });
        assertThat(examples.list(user, course, step, null).revision()).isEqualTo(2);
        assertThat(examples.list(user, course, step, session.sessionId()).revision()).isEqualTo(1);
    }
    @Test void examPinsExampleRevisionBeforeStartingItsSession() {
        tx.executeWithoutResult(s -> {
            var policy = em.find(TitlePolicy.class, TitleRank.BEGINNER);
            if (policy == null) { policy = construct(TitlePolicy.class); fields(policy, Map.of("targetRank", TitleRank.BEGINNER)); em.persist(policy); }
            fields(policy, Map.of("requiredTrainingCount", 0L, "passingScore", 70, "practiceContentId", firstContent));
        });
        var exam = titles.create(user, null);
        tx.executeWithoutResult(s -> seed(2));
        var session = sessions.create(new TrainingSessionCreateRequestDto(firstContent, null, LearningFocus.BOTH, exam.id()), user);
        assertThat(sessions.getSession(session.sessionId(), user).content().practiceExampleRevision()).isEqualTo(1);
        tx.executeWithoutResult(s -> assertThat(em.find(TitleExam.class, exam.id()).getPracticeExampleRevision()).isEqualTo(1));
    }
    @Test void audioReturnsBinaryCachesAndSupportsConditionalRequests() throws Exception {
        byte[] mp3 = new byte[]{'I','D','3',4,0,0};
        when(synthesizer.synthesize(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return mp3;
        });
        String path = "/api/practice-examples/" + firstId + "/audio";
        var response = mvc.perform(get(path).with(auth(user))).andExpect(status().isOk()).andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Cache-Control", "private, max-age=86400")).andExpect(content().bytes(mp3)).andReturn().getResponse();
        mvc.perform(get(path).with(auth(user)).header("If-None-Match", response.getHeader("ETag")))
                .andExpect(status().isNotModified()).andExpect(content().bytes(new byte[0]));
        verify(synthesizer, times(1)).synthesize(eq(examples.audioSource(user, firstId).text()), eq(ExampleAudioService.DEFAULT_VOICE), eq(0.92));
        mvc.perform(get(path).with(auth(user)).param("text", "arbitrary")).andExpect(status().isBadRequest());
        mvc.perform(get(path).with(auth(user)).param("voice", "unapproved")).andExpect(status().isBadRequest());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test void failedAudioIsJsonAndNeverCachedAsSuccess() throws Exception {
        when(synthesizer.synthesize(anyString(), anyString(), anyDouble())).thenThrow(new PracticeExampleException(429, "TTS_RATE_LIMITED"));
        mvc.perform(get("/api/practice-examples/"+firstId+"/audio").with(auth(user)))
                .andExpect(status().isTooManyRequests()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("TTS_RATE_LIMITED"));
        var next = examples.list(user, course, step, null).items().get(1).id();
        doReturn("not mp3".getBytes()).when(synthesizer).synthesize(anyString(), anyString(), anyDouble());
        mvc.perform(get("/api/practice-examples/"+next+"/audio").with(auth(user))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TTS_UNAVAILABLE"));
    }
    @Test void rejectsUnpublishedCourseWrongStepAndMissingExamples() throws Exception {
        mvc.perform(get("/api/courses/"+(course+999999)+"/steps/"+step+"/practice-examples").with(auth(user))).andExpect(status().isNotFound());
        assertStatus(409, () -> sessions.create(new TrainingSessionCreateRequestDto(firstContent, step+999999, LearningFocus.BOTH), user));
        assertStatus(404, () -> examples.audioSource(user, "does-not-exist"));
        Long emptyStep = tx.execute(s -> {
            var st=construct(CourseStep.class); fields(st,Map.of("course",em.find(Course.class,course),"stepType",CourseStepType.PRACTICE,
                    "stepOrder",2,"title","미준비 단계","required",true));em.persist(st);return st.getId();
        });
        assertStatus(503, () -> examples.list(user, course, emptyStep, null));
        tx.executeWithoutResult(s -> ReflectionTestUtils.setField(em.find(Course.class, course), "status", PublishStatus.HIDDEN));
        assertStatus(404, () -> examples.list(user, course, step, null));
        assertStatus(404, () -> examples.audioSource(user, firstId));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL", matches=".+")
    void postgresRejectsContentMutationAndIncompletePublication() {
        assertConstraint(() -> tx.executeWithoutResult(s -> em.createNativeQuery("update practice_contents set script_text='changed' where id=:id").setParameter("id",firstContent).executeUpdate()));
        assertConstraint(() -> tx.executeWithoutResult(s -> em.createNativeQuery("delete from practice_examples where id=:id").setParameter("id",firstId).executeUpdate()));
        assertConstraint(() -> tx.executeWithoutResult(s -> em.createNativeQuery("insert into practice_example_sets(step_id,revision,education_revision_id,published_at) select step_id,999,education_revision_id,published_at from practice_example_sets where step_id=:step and revision=1").setParameter("step",step).executeUpdate()));
        assertThat(examples.list(user,course,step,null).items()).hasSize(5);
    }
    @Test void distributedCacheLeaseAndLimitsAreEnforced() throws Exception {
        var now = OffsetDateTime.parse("2030-01-01T00:00:00Z"); String key = UUID.randomUUID().toString();
        var first = cache.claim(key, user, now);
        assertStatus(503, () -> cache.claim(key, other, now));
        var replacement = cache.claim(key, other, now.plusMinutes(6));
        assertStatus(503, () -> cache.complete(key, first.lease(), new Audio(new byte[]{'I','D','3'}, "\"old\"")));
        cache.complete(key, replacement.lease(), new Audio(new byte[]{'I','D','3'}, "\"new\""));
        assertThat(cache.cached(key).orElseThrow().etag()).isEqualTo("\"new\"");
        for (int i = 0; i < 20; i++) cache.claim(UUID.randomUUID().toString(), user, now.plusHours(1));
        assertStatus(429, () -> cache.claim(UUID.randomUUID().toString(), user, now.plusHours(1)));
        String concurrentKey = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1); var tasks = new ArrayList<Future<Boolean>>();
            for (int i=0;i<4;i++) tasks.add(pool.submit(() -> { start.await(); try { cache.claim(concurrentKey, other, now.plusHours(2)); return true; } catch(PracticeExampleException e) { assertThat(e.status()).isEqualTo(503); return false; } }));
            start.countDown(); int acquired=0; for(var task:tasks) if(task.get(15, TimeUnit.SECONDS)) acquired++;
            assertThat(acquired).isEqualTo(1);
        }
    }
    private RequestPostProcessor auth(Long id) { return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id), null, List.of())); }
    private void assertStatus(int status, Runnable run) { assertThatThrownBy(run::run).isInstanceOfSatisfying(PracticeExampleException.class,e -> assertThat(e.status()).isEqualTo(status)); }
    private void assertConstraint(Runnable run) {
        assertThatThrownBy(run::run).satisfies(error -> {
            Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
            assertThat(cause).isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException)cause).getSQLState()).isEqualTo("23514");
        });
    }
    private static void fields(Object target, Map<String,Object> values) { values.forEach((k,v) -> ReflectionTestUtils.setField(target,k,v)); }
    private static <T> T construct(Class<T> type) { try { var ctor=type.getDeclaredConstructor(); ctor.setAccessible(true); return ctor.newInstance(); } catch(Exception e) { throw new AssertionError(e); } }
}
