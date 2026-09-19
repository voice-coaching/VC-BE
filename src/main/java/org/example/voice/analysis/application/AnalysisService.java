package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.AnalysisResultData;
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
    public AnalysisResultData getCompleted(Long analysisId, Long userId) {
        AnalysisResultData result = analysisResultReader.findOwnedData(analysisId, userId)
                .orElseThrow(AnalysisNotFoundException::new);
        if (!result.isCompleted()) {
            throw new AnalysisNotCompletedException();
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AnalysisResultData getBySession(Long sessionId, Long userId) {
        return analysisResultReader.findLatestBySessionData(sessionId, userId)
                .orElseThrow(SessionAnalysisNotFoundException::new);
    }
}
