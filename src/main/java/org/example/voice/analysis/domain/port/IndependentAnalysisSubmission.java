package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;

public interface IndependentAnalysisSubmission {
    void lockOwner(Long ownerId);
    void assertSession(Long sessionId, Long ownerId);
    void enqueue(AnalysisWorkerRequest request, Long ownerId);
}
