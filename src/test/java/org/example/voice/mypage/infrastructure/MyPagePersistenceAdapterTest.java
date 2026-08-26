package org.example.voice.mypage.infrastructure;

import jakarta.persistence.EntityManager;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;
import org.example.voice.mypage.domain.model.MyPageData;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(NoOpCacheConfig.class)
@Transactional
class MyPagePersistenceAdapterTest {
    @Autowired MyPagePersistenceAdapter adapter;
    @Autowired EntityManager entityManager;

    @Test void readsAggregatesAndDeletesOwnedHistory() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        PracticeContent content = construct(PracticeContent.class);
        set(content, "contentType", ContentType.SENTENCE); set(content, "learningFocus", LearningFocus.PRONUNCIATION);
        set(content, "title", "된소리 연습"); set(content, "scriptText", "쌀을 씻어요");
        set(content, "difficulty", Difficulty.BEGINNER); set(content, "status", PublishStatus.PUBLISHED);
        set(content, "targetPronunciations", java.util.List.of("TENSE_SS"));
        set(content, "createdAt", now); set(content, "updatedAt", now); set(content, "publishedAt", now);
        entityManager.persist(content);

        TrainingSession session = TrainingSession.create(1L, content, null, LearningFocus.PRONUNCIATION);
        set(session, "status", TrainingSessionStatus.COMPLETED); set(session, "completedAt", now);
        set(session, "totalLearningSeconds", 120); entityManager.persist(session);
        VoiceRecording recording = VoiceRecording.create(session, 1, "audio", "audio/webm", 100L, 1000);
        set(recording, "selected", true); set(recording, "qualityStatus", RecordingQualityStatus.PASS);
        entityManager.persist(recording);
        AnalysisResult analysis = AnalysisResult.pending(recording);
        set(analysis, "status", AnalysisStatus.COMPLETED); set(analysis, "transcript", "쌀을 씻어요");
        set(analysis, "overallScore", new BigDecimal("80")); set(analysis, "pronunciationScore", new BigDecimal("70"));
        set(analysis, "intonationScore", new BigDecimal("90")); set(analysis, "analyzedAt", now);
        entityManager.persist(analysis);
        for (int i = 1; i <= 3; i++) {
            AnalysisSegment segment = construct(AnalysisSegment.class);
            set(segment, "analysisResult", analysis); set(segment, "sequenceNo", i); set(segment, "expectedText", "쌀");
            set(segment, "recognizedText", "살"); set(segment, "matchType", SegmentMatchType.SUBSTITUTION);
            set(segment, "resultStatus", SegmentResultStatus.NEEDS_IMPROVEMENT); set(segment, "targetUnit", "TENSE_SS");
            set(segment, "errorType", "TENSE_TO_PLAIN"); set(segment, "pronunciationScore", new BigDecimal("55"));
            entityManager.persist(segment);
        }
        entityManager.flush(); entityManager.clear();

        MyPageData.HistoryPage history = adapter.findHistory(1L, ContentType.SENTENCE, null,
                now.minusDays(1), now.plusDays(1), 0, 20);
        assertThat(history.items()).hasSize(1);
        assertThat(adapter.findHistoryDetail(1L, session.getId())).isPresent();
        assertThat(adapter.calculateStatistics(1L, now.minusDays(1), now.plusDays(1), now.minusDays(1),
                now.plusDays(1)).totalSessionCount()).isEqualTo(1);
        assertThat(adapter.findUnitScores(1L, now.minusDays(1), now.plusDays(1)).items()).singleElement()
                .extracting(MyPageData.UnitScore::attemptCount).isEqualTo(3);
        assertThat(adapter.findScoreTrend(1L, "PRONUNCIATION", now.minusDays(1), now.plusDays(1)).items()).hasSize(1);
        assertThat(adapter.findRecommendations(java.util.List.of("TENSE_SS"), ContentType.SENTENCE, 10).items()).hasSize(1);

        adapter.deleteHistory(session.getId()); entityManager.flush(); entityManager.clear();
        assertThat(adapter.sessionExists(session.getId())).isFalse();
    }

    private <T> T construct(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); return constructor.newInstance();
    }
    private void set(Object target, String field, Object value) { ReflectionTestUtils.setField(target, field, value); }
}
