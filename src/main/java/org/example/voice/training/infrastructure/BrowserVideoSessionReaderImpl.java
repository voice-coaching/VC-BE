package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.port.BrowserVideoSessionReader;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class BrowserVideoSessionReaderImpl implements BrowserVideoSessionReader {
    private final TrainingSessionJpaRepository sessions;

    @Override
    @Transactional(readOnly = true)
    public Source ownedSource(Long sessionId, Long userId) {
        if (userId == null || userId <= 0) throw new BaseException(ErrorCode.UNAUTHORIZED);
        // Do not use the legacy compatibility findByIdAndUserId default: it
        // delegates to findById without filtering the owner in this revision.
        var session = sessions.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> new BaseException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
        if (session.getStatus() != TrainingSessionStatus.RECORDING
                && session.getStatus() != TrainingSessionStatus.UPLOADING
                && session.getStatus() != TrainingSessionStatus.FAILED)
            throw new BaseException(ErrorCode.INVALID_SESSION_STATE);
        if (session.getLearningFocus() != LearningFocus.PRONUNCIATION)
            throw new BaseException(ErrorCode.ANALYSIS_FOCUS_NOT_SUPPORTED);
        String script = session.analysisScriptText();
        if (script == null || script.isBlank() || script.codePointCount(0, script.length()) > 4000)
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        return new Source(session.getContent().getId(), script);
    }
}
