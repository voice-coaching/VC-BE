package org.example.voice.title;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument;
import org.example.voice.analysis.infrastructure.canonical.CanonicalCommittedResultReader;
import org.example.voice.title.infrastructure.TitlePersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CanonicalTitleScoreTest {
    EntityManager em = mock(EntityManager.class);
    CanonicalCommittedResultReader reader = mock(CanonicalCommittedResultReader.class);
    AnalysisResult analysis = mock(AnalysisResult.class);
    CanonicalCallbackDocument document = mock(CanonicalCallbackDocument.class);
    TitlePersistence titles = new TitlePersistence(em, reader);

    @BeforeEach void setup() {
        @SuppressWarnings("unchecked") TypedQuery<AnalysisResult> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(AnalysisResult.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenAnswer(call -> Stream.of(analysis));
        when(reader.findCurrent(analysis)).thenReturn(Optional.of(document));
        when(document.hasRequiredStorageEvidence()).thenReturn(true);
        when(document.decision()).thenReturn(new CanonicalCallbackDocument.Decision(
                CanonicalCallbackDocument.DecisionStatus.ACCEPT, null, null));
        when(document.representation()).thenReturn("INLINE");
        when(document.feedbackDeliveryAllowed()).thenReturn(true);
        when(document.adapterStatus()).thenReturn("READY");
        when(document.generationStatus()).thenReturn("DETERMINISTIC_FALLBACK");
        when(document.overallScore()).thenReturn(new BigDecimal("83.50"));
    }

    @Test void usesScoreFromVerifiedCurrentServerResult() {
        assertThat(titles.score(1L,2L,3L,4L)).contains(new BigDecimal("83.50"));
        assertThat(titles.coachingScoreUnavailable(1L,2L,3L,4L)).isFalse();
    }
    @Test void missingPersistenceProofCannotGrade() {
        when(reader.findCurrent(analysis)).thenReturn(Optional.empty());
        assertThat(titles.score(1L,2L,3L,4L)).isEmpty();
        assertThat(titles.coachingScoreUnavailable(1L,2L,3L,4L)).isTrue();
    }
    @Test void unscorableResultIsNotZeroAndCannotGrade() {
        when(document.overallScore()).thenReturn(null);
        assertThat(titles.score(1L,2L,3L,4L)).isEmpty();
        assertThat(titles.coachingScoreUnavailable(1L,2L,3L,4L)).isTrue();
    }
    @Test void rejectedResultCannotGradeEvenIfItHasAScore() {
        when(document.decision()).thenReturn(new CanonicalCallbackDocument.Decision(
                CanonicalCallbackDocument.DecisionStatus.REJECT, null, null));
        assertThat(titles.score(1L,2L,3L,4L)).isEmpty();
    }
    @Test void identityMismatchCannotGrade() {
        when(reader.findCurrent(analysis)).thenThrow(new IllegalStateException("CANONICAL_RESULT_UNAVAILABLE"));
        assertThat(titles.score(1L,2L,3L,4L)).isEmpty();
    }
    @Test void outOfRangeAndUndeliverableScoresCannotGrade() {
        when(document.overallScore()).thenReturn(new BigDecimal("101"));
        assertThat(titles.score(1L,2L,3L,4L)).isEmpty();
        when(document.overallScore()).thenReturn(new BigDecimal("83.50"));
        when(document.feedbackDeliveryAllowed()).thenReturn(false);
        assertThat(titles.score(1L,2L,3L,4L)).isEmpty();
    }
    @Test void audioExamScopeAllowsTheLinkedSessionWithoutSkippingCourseRestrictions() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var scope = new org.example.voice.analysis.infrastructure.canonical.CanonicalRequestScope(jdbc);
        var recording = mock(org.example.voice.training.domain.entity.VoiceRecording.class);
        var session = mock(org.example.voice.training.domain.entity.TrainingSession.class);
        when(analysis.getRecording()).thenReturn(recording);
        when(recording.getTrainingSession()).thenReturn(session);
        when(recording.getQualityStatus()).thenReturn(org.example.voice.training.domain.type.RecordingQualityStatus.PASS);
        when(session.getLearningFocus()).thenReturn(org.example.voice.practicecontent.domain.type.LearningFocus.PRONUNCIATION);
        when(session.getCourseStepId()).thenReturn(null);
        when(session.getCourseEducationRevisionId()).thenReturn(null);
        assertThat(scope.eligible(analysis, false)).isTrue();
        verifyNoInteractions(jdbc);
        when(session.getCourseStepId()).thenReturn(99L);
        assertThat(scope.eligible(analysis, false)).isFalse();
    }
    @Test void capabilitiesAdvertiseTitleExamsButKeepAdmissionReadiness() {
        var readiness = mock(org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness.class);
        var audiovisual = mock(org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness.class);
        var submissions = mock(org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission.class);
        var service = new org.example.voice.analysis.application.CanonicalCapabilitiesService(readiness, audiovisual, submissions);
        var value = service.read();
        assertThat(value.scopes().get("TITLE_EXAM").supported()).isTrue();
        assertThat(value.admissionEnabled()).isFalse();
        assertThat(value.scopes().get("COURSE").supported()).isFalse();
    }
}
