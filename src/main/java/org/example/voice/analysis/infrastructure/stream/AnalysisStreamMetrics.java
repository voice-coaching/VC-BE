package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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

    public AnalysisStreamMetrics(MeterRegistry registry) {
        requestsPublished = registry.counter("voice.analysis.stream.requests.published");
        requestPublishFailures = registry.counter("voice.analysis.stream.requests.publish.failures");
        resultsApplied = registry.counter("voice.analysis.stream.results.applied");
        resultsIgnored = registry.counter("voice.analysis.stream.results.ignored");
        resultDeliveryFailures = registry.counter("voice.analysis.stream.results.delivery.failures");
        resultsDeadLettered = registry.counter("voice.analysis.stream.results.dead.lettered");
        executionTimeouts = registry.counter("voice.analysis.execution.timeouts");
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
}
