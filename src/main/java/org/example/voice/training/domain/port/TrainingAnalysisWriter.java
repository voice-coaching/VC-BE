package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.AnalysisRequestData;
import org.example.voice.training.domain.model.AnalysisRetryData;

import java.util.UUID;

public interface TrainingAnalysisWriter {

    AnalysisRequestData createPending(Long recordingId, UUID requestEventId);

    AnalysisRetryData retry(Long previousAnalysisId, Long recordingId, Integer retryCount, UUID requestEventId);
}
