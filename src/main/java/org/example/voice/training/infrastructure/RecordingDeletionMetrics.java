package org.example.voice.training.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Storage deletion metrics with no user, session, object, or reason labels. */
@Component
public class RecordingDeletionMetrics {

    private final Counter deleted;
    private final Counter retries;
    private final Counter terminalFailures;
    private final Counter deliveryTransactionFailures;
    private final Counter observationFailures;
    private final AtomicLong pending = new AtomicLong(-1);
    private final AtomicLong failed = new AtomicLong(-1);
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong(-1);

    public RecordingDeletionMetrics(MeterRegistry registry) {
        deleted = registry.counter("voice.storage.deletion.completed");
        retries = registry.counter("voice.storage.deletion.retries");
        terminalFailures = registry.counter("voice.storage.deletion.terminal.failures");
        deliveryTransactionFailures = registry.counter(
                "voice.storage.deletion.delivery.transaction.failures"
        );
        observationFailures = registry.counter("voice.storage.deletion.observation.failures");
        Gauge.builder("voice.storage.deletion.pending", pending, AtomicLong::doubleValue)
                .register(registry);
        Gauge.builder("voice.storage.deletion.failed", failed, AtomicLong::doubleValue)
                .register(registry);
        Gauge.builder(
                "voice.storage.deletion.oldest.pending.age.seconds",
                oldestPendingAgeSeconds,
                AtomicLong::doubleValue
        ).register(registry);
    }

    public void deleted() {
        deleted.increment();
    }

    public void retryScheduled() {
        retries.increment();
    }

    public void terminalFailure() {
        terminalFailures.increment();
    }

    public void deliveryTransactionFailed() {
        deliveryTransactionFailures.increment();
    }

    public void observe(long pendingCount, long failedCount, long oldestAgeSeconds) {
        pending.set(pendingCount);
        failed.set(failedCount);
        oldestPendingAgeSeconds.set(oldestAgeSeconds);
    }

    public void observationFailed() {
        observationFailures.increment();
        observe(-1, -1, -1);
    }
}
