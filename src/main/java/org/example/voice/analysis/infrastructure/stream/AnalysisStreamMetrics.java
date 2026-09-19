package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded-cardinality transport metrics; no user, object, or event identifiers are labels. */
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisStreamMetrics {

    private final Counter requestsPublished;
    private final Counter requestPublishFailures;
    private final Counter resultsApplied;
    private final Counter resultsIgnored;
    private final Counter resultDeliveryFailures;
    private final Counter resultsDeadLettered;
    private final Counter executionTimeouts;
    private final Counter cancellationsPublished;
    private final Counter cancellationPublishFailures;
    private final Counter retentionCleanups;
    private final Counter retentionFailures;
    private final Counter transportObservationFailures;
    private final Counter outboxObservationFailures;
    private final AtomicLong requestOutstanding = unknownGauge();
    private final AtomicLong requestPending = unknownGauge();
    private final AtomicLong requestDeadLetter = unknownGauge();
    private final AtomicLong resultOutstanding = unknownGauge();
    private final AtomicLong resultPending = unknownGauge();
    private final AtomicLong resultDeadLetter = unknownGauge();
    private final AtomicLong requestOutboxPending = unknownGauge();
    private final AtomicLong requestOutboxOldestAgeSeconds = unknownGauge();
    private final AtomicLong cancellationOutboxPending = unknownGauge();
    private final AtomicLong cancellationOutboxOldestAgeSeconds = unknownGauge();

    public AnalysisStreamMetrics(MeterRegistry registry) {
        requestsPublished = registry.counter("voice.analysis.stream.requests.published");
        requestPublishFailures = registry.counter("voice.analysis.stream.requests.publish.failures");
        resultsApplied = registry.counter("voice.analysis.stream.results.applied");
        resultsIgnored = registry.counter("voice.analysis.stream.results.ignored");
        resultDeliveryFailures = registry.counter("voice.analysis.stream.results.delivery.failures");
        resultsDeadLettered = registry.counter("voice.analysis.stream.results.dead.lettered");
        executionTimeouts = registry.counter("voice.analysis.execution.timeouts");
        cancellationsPublished = registry.counter("voice.analysis.cancellations.published");
        cancellationPublishFailures = registry.counter("voice.analysis.cancellations.publish.failures");
        retentionCleanups = registry.counter("voice.analysis.retention.cleaned");
        retentionFailures = registry.counter("voice.analysis.retention.failures");
        transportObservationFailures = registry.counter(
                "voice.analysis.stream.observation.failures"
        );
        outboxObservationFailures = registry.counter(
                "voice.analysis.outbox.observation.failures"
        );
        gauge(registry, "voice.analysis.stream.request.outstanding", requestOutstanding);
        gauge(registry, "voice.analysis.stream.request.pel.pending", requestPending);
        gauge(registry, "voice.analysis.stream.request.dead.letter.entries", requestDeadLetter);
        gauge(registry, "voice.analysis.stream.result.outstanding", resultOutstanding);
        gauge(registry, "voice.analysis.stream.result.pel.pending", resultPending);
        gauge(registry, "voice.analysis.stream.result.dead.letter.entries", resultDeadLetter);
        gauge(registry, "voice.analysis.outbox.request.pending", requestOutboxPending);
        gauge(
                registry,
                "voice.analysis.outbox.request.oldest.pending.age.seconds",
                requestOutboxOldestAgeSeconds
        );
        gauge(registry, "voice.analysis.outbox.cancellation.pending", cancellationOutboxPending);
        gauge(
                registry,
                "voice.analysis.outbox.cancellation.oldest.pending.age.seconds",
                cancellationOutboxOldestAgeSeconds
        );
    }

    public void requestPublished() {
        requestsPublished.increment();
    }

    public void requestPublishFailed() {
        requestPublishFailures.increment();
    }

    public void resultIngested(AnalysisResultIngestionDisposition disposition) {
        if (disposition == AnalysisResultIngestionDisposition.APPLIED) {
            resultsApplied.increment();
        } else {
            resultsIgnored.increment();
        }
    }

    public void resultDeliveryFailed() {
        resultDeliveryFailures.increment();
    }

    public void resultDeadLettered() {
        resultsDeadLettered.increment();
    }

    public void executionTimedOut() {
        executionTimeouts.increment();
    }

    public void cancellationPublished() {
        cancellationsPublished.increment();
    }

    public void cancellationPublishFailed() {
        cancellationPublishFailures.increment();
    }

    public void retentionCleaned() {
        retentionCleanups.increment();
    }

    public void retentionFailed() {
        retentionFailures.increment();
    }

    public void observeTransport(
            long requestOutstandingCount,
            long requestPendingCount,
            long requestDeadLetterCount,
            long resultOutstandingCount,
            long resultPendingCount,
            long resultDeadLetterCount
    ) {
        requestOutstanding.set(requestOutstandingCount);
        requestPending.set(requestPendingCount);
        requestDeadLetter.set(requestDeadLetterCount);
        resultOutstanding.set(resultOutstandingCount);
        resultPending.set(resultPendingCount);
        resultDeadLetter.set(resultDeadLetterCount);
    }

    public void transportObservationFailed() {
        transportObservationFailures.increment();
        observeTransport(-1, -1, -1, -1, -1, -1);
    }

    public void observeOutboxes(
            long requestPendingCount,
            long requestOldestAgeSeconds,
            long cancellationPendingCount,
            long cancellationOldestAgeSeconds
    ) {
        requestOutboxPending.set(requestPendingCount);
        requestOutboxOldestAgeSeconds.set(requestOldestAgeSeconds);
        cancellationOutboxPending.set(cancellationPendingCount);
        cancellationOutboxOldestAgeSeconds.set(cancellationOldestAgeSeconds);
    }

    public void outboxObservationFailed() {
        outboxObservationFailures.increment();
        observeOutboxes(-1, -1, -1, -1);
    }

    private static AtomicLong unknownGauge() {
        return new AtomicLong(-1);
    }

    private static void gauge(MeterRegistry registry, String name, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::doubleValue).register(registry);
    }
}
