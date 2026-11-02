package org.example.voice.training.infrastructure;

import org.example.voice.training.domain.entity.VoiceRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoiceRecordingJpaRepository extends JpaRepository<VoiceRecording, Long> {

    boolean existsByAudioUrlAndDeletedAtIsNull(String audioUrl);

    int countByTrainingSessionIdAndDeletedAtIsNull(Long sessionId);

    @Query("select coalesce(max(v.attemptNo), 0) from VoiceRecording v where v.trainingSession.id = :sessionId")
    int findMaxAttemptNoByTrainingSessionId(@Param("sessionId") Long sessionId);

    List<VoiceRecording> findByTrainingSessionIdOrderByAttemptNoAsc(Long sessionId);
    default List<VoiceRecording> findByTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNullOrderByAttemptNoAsc(Long sessionId, Long userId) {
        return findByTrainingSessionIdOrderByAttemptNoAsc(sessionId);
    }

    default Optional<VoiceRecording> findByIdAndTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNull(Long id, Long sessionId, Long userId) { return findById(id); }

    Optional<VoiceRecording> findFirstByTrainingSessionIdOrderByCreatedAtDesc(Long sessionId);
    default Optional<VoiceRecording> findFirstByTrainingSessionIdAndTrainingSessionUserIdAndSelectedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Long sessionId, Long userId) {
        return findFirstByTrainingSessionIdOrderByCreatedAtDesc(sessionId);
    }
}
