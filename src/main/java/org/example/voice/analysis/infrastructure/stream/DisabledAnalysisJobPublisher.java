package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

/**
 * Keeps the public analysis request fail-closed when the dedicated Stream
 * transport is not configured. The surrounding request transaction rolls back
 * the PENDING result and outbox row rather than stranding a request.
 */
@Repository
@ConditionalOnExpression("'${analysis.transport:disabled}' == 'disabled' and '${analysis.stream.enabled:false}' == 'false'")
public class DisabledAnalysisJobPublisher implements AnalysisJobPublisher {

    @Override
    public void publish(AnalysisWorkerRequest request) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
