package org.example.voice.training.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaRecordingDeletionScheduler implements RecordingDeletionScheduler {

    private final RecordingDeletionOutboxJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void schedule(Long userId, Long sessionId, String objectKey, RecordingDeletionReason reason) {
        if (repository.existsByObjectKey(objectKey)) {
            return;
        }
        repository.save(RecordingDeletionOutbox.pending(userId, sessionId, objectKey, reason));
    }

    @Override
    @Transactional
    public void scheduleAllForUser(Long userId, RecordingDeletionReason reason) {
        List<Object[]> recordings = entityManager.createQuery("""
                select recording.trainingSession.id, recording.audioUrl
                  from VoiceRecording recording
                 where recording.trainingSession.userId = :userId
                """, Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        recordings.forEach(recording -> schedule(
                userId,
                (Long) recording[0],
                (String) recording[1],
                reason
        ));
    }
}
