package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.home.infrastructure.cache.HomeCacheNames;
import org.example.voice.mypage.infrastructure.cache.MyPageCacheNames;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.practicecontent.infrastructure.PracticeContentJpaRepository;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.model.TrainingSessionCancellationData;
import org.example.voice.training.domain.model.TrainingSessionCompletionData;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TrainingSessionWriterImpl implements TrainingSessionWriter {

    private final PracticeContentJpaRepository practiceContentJpaRepository;
    private final TrainingSessionJpaRepository trainingSessionJpaRepository;
    private final VoiceRecordingJpaRepository voiceRecordingJpaRepository;

    @Override
    @Transactional
    @CacheEvict(cacheNames = HomeCacheNames.RECENT_TRAINING, allEntries = true)
    public TrainingSessionCreatedData create(Long userId, Long contentId, Long courseStepId, LearningFocus learningFocus) {
        PracticeContent content = practiceContentJpaRepository.findById(contentId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONTENT_NOT_FOUND));
        TrainingSession session = trainingSessionJpaRepository.save(
                TrainingSession.create(userId, content, courseStepId, learningFocus)
        );
        return new TrainingSessionCreatedData(
                session.getId(),
                session.getContent().getId(),
                session.getCourseStepId(),
                session.getLearningFocus(),
                session.getStatus(),
                session.getStartedAt()
        );
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = HomeCacheNames.RECENT_TRAINING, allEntries = true)
    public void beginUpload(Long sessionId) {
        TrainingSession session = findForUpdate(sessionId);
        if (!session.beginUpload()) {
            throw new BaseException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = HomeCacheNames.RECENT_TRAINING, allEntries = true)
    public void startAnalysis(Long sessionId) {
        TrainingSession session = findForUpdate(sessionId);
        if (!session.startAnalysis()) {
            throw new BaseException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    @Override
    @Transactional
    public void assertAnalysisRetryAllowed(Long sessionId) {
        TrainingSession session = findForUpdate(sessionId);
        if (!session.allowsAnalysisRetry()) {
            throw new BaseException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    private TrainingSession findForUpdate(Long sessionId) {
        return trainingSessionJpaRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = HomeCacheNames.TODAY_STATUS, allEntries = true),
            @CacheEvict(cacheNames = HomeCacheNames.RECENT_TRAINING, allEntries = true),
            @CacheEvict(cacheNames = {
                    MyPageCacheNames.HISTORY,
                    MyPageCacheNames.HISTORY_DETAIL,
                    MyPageCacheNames.STATISTICS,
                    MyPageCacheNames.UNIT_SCORES,
                    MyPageCacheNames.SCORE_TREND,
                    MyPageCacheNames.RECOMMENDATIONS
            }, allEntries = true)
    })
    public TrainingSessionCompletionData complete(Long sessionId, Integer totalLearningSeconds) {
        TrainingSession session = findForUpdate(sessionId);
        if (session.getStatus() != TrainingSessionStatus.ANALYZING) {
            throw new BaseException(ErrorCode.INVALID_SESSION_STATE);
        }
        session.complete(totalLearningSeconds);
        return new TrainingSessionCompletionData(session.getId(), session.getStatus(), session.getCompletedAt());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = HomeCacheNames.TODAY_STATUS, allEntries = true),
            @CacheEvict(cacheNames = HomeCacheNames.RECENT_TRAINING, allEntries = true),
            @CacheEvict(cacheNames = {
                    MyPageCacheNames.HISTORY,
                    MyPageCacheNames.HISTORY_DETAIL,
                    MyPageCacheNames.STATISTICS,
                    MyPageCacheNames.UNIT_SCORES,
                    MyPageCacheNames.SCORE_TREND,
                    MyPageCacheNames.RECOMMENDATIONS
            }, allEntries = true)
    })
    public TrainingSessionCancellationData cancel(Long sessionId) {
        TrainingSession session = findForUpdate(sessionId);
        if (session.getStatus() == TrainingSessionStatus.COMPLETED
                || session.getStatus() == TrainingSessionStatus.CANCELED
                || session.getStatus() == TrainingSessionStatus.FAILED) {
            throw new BaseException(ErrorCode.SESSION_ALREADY_FINISHED);
        }
        session.cancel();
        voiceRecordingJpaRepository
                .findByTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNullOrderByAttemptNoAsc(
                        sessionId,
                        session.getUserId()
                )
                .stream()
                .filter(recording -> !recording.getSelected())
                .forEach(VoiceRecording::delete);
        return new TrainingSessionCancellationData(session.getId(), session.getStatus(), session.getCompletedAt());
    }
}
