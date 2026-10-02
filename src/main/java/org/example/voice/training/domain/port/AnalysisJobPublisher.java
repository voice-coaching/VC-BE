package org.example.voice.training.domain.port;

import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;

public interface AnalysisJobPublisher {

    /** Persist a request for at-least-once delivery to the AI request stream. */
    void publish(AnalysisWorkerRequest request);

    /** Explicit opt-in; non-HTTP adapters must never silently downgrade canonical work. */
    default void publish(AnalysisWorkerRequest request, AnalysisExecutionProfile profile) {
        if (profile != AnalysisExecutionProfile.LEGACY) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
        publish(request);
    }
}
