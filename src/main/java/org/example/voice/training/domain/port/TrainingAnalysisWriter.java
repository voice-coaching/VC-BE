package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.AnalysisRequestData;
import org.example.voice.training.domain.model.AnalysisRetryData;

public interface TrainingAnalysisWriter {

    AnalysisRequestData createPending(Long recordingId);

    AnalysisRetryData retry(Long previousAnalysisId, Long recordingId, Integer retryCount);
}
