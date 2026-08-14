package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.exception.AnalysisNotCompletedException;
import org.example.voice.analysis.exception.AnalysisNotFoundException;
import org.example.voice.analysis.exception.SessionAnalysisNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisService {
    private final AnalysisResultReader analysisResultReader;

    @Transactional(readOnly = true)
    public AnalysisResult getCompleted(Long analysisId, Long userId) {
        AnalysisResult result = analysisResultReader.findOwned(analysisId, userId).orElseThrow(AnalysisNotFoundException::new);
        if (!result.isCompleted()) throw new AnalysisNotCompletedException();
        return result;
    }

    @Transactional(readOnly = true)
    public AnalysisResult getBySession(Long sessionId, Long userId) {
        return analysisResultReader.findLatestBySession(sessionId, userId).orElseThrow(SessionAnalysisNotFoundException::new);
    }
}
