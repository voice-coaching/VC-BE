package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.entity.RecordingUploadIntent;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecordingUploadIntentSweeper {

    private final RecordingUploadIntentJpaRepository repository;
    private final RecordingDeletionScheduler deletionScheduler;

    @Scheduled(fixedDelayString = "${storage.upload-intent-sweep-interval-ms:60000}")
    @Transactional
    public void sweep() {
        for (RecordingUploadIntent intent : repository
                .findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                        RecordingUploadIntentStatus.ISSUED,
                        OffsetDateTime.now(ZoneOffset.UTC)
                )) {
            if (!intent.expire()) {
                continue;
            }
            deletionScheduler.schedule(
                    intent.getUserId(),
                    intent.getTrainingSessionId(),
                    intent.getObjectKey(),
                    RecordingDeletionReason.UPLOAD_EXPIRED
            );
        }
        repository.deleteByStatusInAndResolvedAtBefore(
                List.of(RecordingUploadIntentStatus.CONSUMED, RecordingUploadIntentStatus.EXPIRED),
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
        );
    }
}
