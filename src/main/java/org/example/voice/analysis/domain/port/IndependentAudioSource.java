package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;

public interface IndependentAudioSource {
    byte[] read(IndependentAnalysisAttempt attempt);
}
