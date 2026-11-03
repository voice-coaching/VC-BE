package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.util.Objects;

/** Compare immutable result identity with the active attempt, never with retained history. */
public final class CanonicalResultAttempt {
    private CanonicalResultAttempt() {}

    public static boolean matches(AnalysisResult result, CanonicalCallbackDocument document) {
        var id = document.identity();
        var recording = result.getRecording();
        return Objects.equals(result.getId(), id.analysisId())
                && id.requestId().toString().equals(result.getActiveRequestEventId())
                && id.executionId().toString().equals(result.getActiveExecutionId())
                && id.workerId().toString().equals(result.getWorkerInstanceId())
                && recording != null
                && Objects.equals(recording.getId(), id.recordingId())
                && recording.getTrainingSession().getContent()!=null
                && Objects.equals(recording.getTrainingSession().getContent().getId(), id.contentId());
    }

    /** A backend failure/cancellation must not be replaced by an uncommitted preview. */
    public static boolean visible(AnalysisResult result, CanonicalCallbackDocument document) {
        return matches(result,document)
                && (result.getStatus()==AnalysisStatus.PENDING
                    || result.getStatus()==AnalysisStatus.PROCESSING
                    || document.identity().eventId().equals(result.getCanonicalResultEventId()));
    }
}
