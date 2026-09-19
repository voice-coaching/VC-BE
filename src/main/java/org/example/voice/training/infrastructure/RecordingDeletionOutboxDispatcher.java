package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecordingDeletionOutboxDispatcher {

    private final RecordingDeletionOutboxJpaRepository repository;
    private final RecordingDeletionDelivery delivery;
    private final RecordingDeletionMetrics metrics;

    @Scheduled(fixedDelayString = "${storage.deletion-dispatch-interval-ms:30000}")
    public void dispatch() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Long> candidates;
        try {
            candidates = repository.findDispatchCandidateIds(
                    RecordingDeletionStatus.PENDING,
                    now,
                    PageRequest.of(0, 100)
            );
        } catch (RuntimeException error) {
            metrics.deliveryTransactionFailed();
            observe(now);
            return;
        }
        for (Long id : candidates) {
            try {
                delivery.deliver(id);
            } catch (RuntimeException error) {
                metrics.deliveryTransactionFailed();
            }
        }
        try {
            delivery.purgeDeletedBefore(now.minusDays(30));
        } catch (RuntimeException error) {
            metrics.deliveryTransactionFailed();
        }
        observe(now);
    }

    private void observe(OffsetDateTime now) {
        try {
            long pending = repository.countByStatus(RecordingDeletionStatus.PENDING);
            long failed = repository.countByStatus(RecordingDeletionStatus.FAILED);
            long oldestAge = repository
                    .findFirstByStatusOrderByCreatedAtAsc(RecordingDeletionStatus.PENDING)
                    .map(value -> Math.max(0, ChronoUnit.SECONDS.between(value.getCreatedAt(), now)))
                    .orElse(0L);
            metrics.observe(pending, failed, oldestAge);
        } catch (RuntimeException error) {
            metrics.observationFailed();
        }
    }
}
