package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.application.CanonicalAnalysisQueryService;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.CanonicalExecutionRegistry;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CanonicalPersistenceVisibilityTest {
    final AnalysisResult result=mock(AnalysisResult.class);
    final CanonicalCommittedResultReader committed=mock(CanonicalCommittedResultReader.class);
    final CanonicalPublishedResults published=new CanonicalPublishedResults(null,null);
    final CanonicalDeliverySpool spool=mock(CanonicalDeliverySpool.class);
    final CanonicalCompletionEligibility completion=new CanonicalCompletionEligibility(committed);
    CanonicalAnalysisQueryService queries;
    CanonicalCallbackDocument document;

    @BeforeEach void setup() throws Exception {
        var contract=new RunPodContract();
        document=CanonicalCallbackDocument.parse(V6AudioFixtures.accepted().toString().getBytes(StandardCharsets.UTF_8),contract);
        var id=document.identity();
        var recording=mock(VoiceRecording.class);var session=mock(TrainingSession.class);var content=mock(PracticeContent.class);
        when(result.getId()).thenReturn(id.analysisId());when(result.getRecording()).thenReturn(recording);
        when(recording.getId()).thenReturn(id.recordingId());when(recording.getTrainingSession()).thenReturn(session);
        when(session.getUserId()).thenReturn(9L);when(session.getContent()).thenReturn(content);when(content.getId()).thenReturn(id.contentId());
        when(result.getActiveRequestEventId()).thenReturn(id.requestId().toString());
        when(result.getActiveExecutionId()).thenReturn(id.executionId().toString());
        when(result.getWorkerInstanceId()).thenReturn(id.workerId().toString());
        when(result.getAnalysisProfile()).thenReturn(RunPodContract.AUDIOVISUAL_PROFILE);
        when(result.getStatus()).thenReturn(AnalysisStatus.PROCESSING);
        var results=mock(AnalysisResultJpaRepository.class);when(results.findById(id.analysisId())).thenReturn(Optional.of(result));
        var visibility=mock(CanonicalViewVisibility.class);when(visibility.unchanged(result,9L)).thenReturn(true);
        var actions=new CanonicalActionPolicy(completion);
        queries=new CanonicalAnalysisQueryService(results,mock(CanonicalExecutionRegistry.class),committed,visibility,
                new CanonicalPublicProjection(contract),actions,spool,published);
        published.publish(id.analysisId(),id.workerId(),document);
    }

    @Test void publishedFeedbackIsVisibleWhileCompletionWaitsForCommit() {
        var view=queries.get(result.getId(),9L);
        assertThat(view.jobStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(view.canonicalAnalysis()).isNotNull();assertThat(view.rawResult()).isNotNull();
        assertThat(view.persistenceStatus()).isEqualTo("SAVING");
        assertThat(view.actions().canComplete()).isFalse();
        assertThat(completion.allows(result)).isFalse();
    }

    @Test void onlyTheMatchingCommittedResultAllowsCompletionAndDoesNotRequireAScore() {
        when(result.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(result.getCanonicalResultEventId()).thenReturn(document.identity().eventId());
        when(committed.findCurrent(result)).thenReturn(Optional.of(document));
        var view=queries.get(result.getId(),9L);
        assertThat(document.overallScore()).isNull();
        assertThat(view.persistenceStatus()).isEqualTo("SAVED");
        assertThat(view.actions().canComplete()).isTrue();assertThat(completion.allows(result)).isTrue();
    }

    @Test void completedFlagAloneCannotAuthorizeAnUncommittedPreview() {
        when(result.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(result.getCanonicalResultEventId()).thenReturn(document.identity().eventId());
        assertThat(queries.get(result.getId(),9L).persistenceStatus()).isEqualTo("SAVING");
        assertThat(queries.get(result.getId(),9L).actions().canComplete()).isFalse();
        assertThat(completion.allows(result)).isFalse();
    }

    @Test void retainedPreviousAttemptCannotAuthorizeCurrentCompletion() {
        when(result.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(result.getCanonicalResultEventId()).thenReturn(UUID.randomUUID());
        when(committed.findCurrent(result)).thenReturn(Optional.of(document));
        assertThat(completion.allows(result)).isFalse();
        assertThat(queries.get(result.getId(),9L).actions().canComplete()).isFalse();
    }

    @Test void failedOrUndeliverableResultsNeverAllowCompletion() {
        when(result.getStatus()).thenReturn(AnalysisStatus.FAILED);
        when(committed.findCurrent(result)).thenReturn(Optional.of(document));
        assertThat(completion.allows(result)).isFalse();
        when(result.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(result.getCanonicalResultEventId()).thenReturn(document.identity().eventId());
        var withheld=mock(CanonicalCallbackDocument.class);
        when(withheld.identity()).thenReturn(document.identity());when(withheld.status()).thenReturn(AnalysisStatus.COMPLETED);
        when(committed.findCurrent(result)).thenReturn(Optional.of(withheld));
        assertThat(completion.allows(result)).isFalse();
    }

    @Test void newAttemptDoesNotExposeAPreviousPublishedResult() {
        when(result.getActiveExecutionId()).thenReturn(UUID.randomUUID().toString());
        var view=queries.get(result.getId(),9L);
        assertThat(view.canonicalAnalysis()).isNull();assertThat(view.persistenceStatus()).isEqualTo("NONE");
        assertThat(view.actions().canComplete()).isFalse();
    }

    @Test void independentCompletionStillRequiresAStoredDeliveredResult() {
        when(result.isIndependentExecution()).thenReturn(true);
        assertThat(completion.allows(result)).isFalse();
        when(result.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(result.getCanonicalResultEventId()).thenReturn(UUID.randomUUID());
        assertThat(completion.allows(result)).isFalse();
        when(result.isCoachingDelivered()).thenReturn(true);
        assertThat(completion.allows(result)).isTrue();
        verifyNoInteractions(committed);
    }
}
