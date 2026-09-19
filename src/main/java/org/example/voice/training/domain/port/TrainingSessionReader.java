package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.TrainingSessionDetailData;
import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.util.Optional;

public interface TrainingSessionReader {

    boolean existsContent(Long contentId);

    boolean existsAvailableContent(Long contentId);

    Optional<TrainingSessionDetailData> findSessionDetail(Long sessionId, Long userId);

    boolean existsSession(Long sessionId, Long userId);

    Optional<TrainingSessionStatus> findSessionStatus(Long sessionId, Long userId);
}
