package org.example.voice.title.domain.port;

import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.*;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;
import java.math.BigDecimal;
import java.util.Optional;

public interface TitleRepository {
    Optional<UserTitle> title(Long userId);
    UserTitle save(UserTitle title);
    Optional<TitlePolicy> policy(TitleRank target);
    long completedCount(Long userId);
    Optional<TitleExam> owned(Long id, Long userId);
    Optional<TitleExam> active(Long userId, TitleRank target);
    TitleExam save(TitleExam exam);
    Optional<Long> requested(Long userId, String digest);
    void remember(TitleExamRequest request);
    boolean availableContent(Long id);
    boolean ownedAnalysis(Long id, Long userId);
    Optional<BigDecimal> score(Long analysisId, Long userId, Long sessionId, Long contentId);
    Optional<TrainingSessionCreatedData> reusableSession(Long sessionId, Long userId);
}
