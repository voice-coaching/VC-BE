package org.example.voice.payment.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.payment.application.PaymentRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = {"payment.test.enabled", "payment.test.recovery.enabled"}, havingValue = "true")
public class PaymentRecoveryScheduler {
    private final PaymentRecoveryService recovery;
    private final MeterRegistry metrics;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void poll() {
        try {
            var result = recovery.recover();
            count("applied", result.applied());
            count("not_completed", result.missing());
            count("failed", result.failed());
            count("skipped", result.skipped());
            if (result.failed() > 0) log.warn("Test payment recovery had {} failures; retry on next sweep", result.failed());
        } catch (RuntimeException e) {
            metrics.counter("payment.test.recovery.batch.failures").increment();
            log.warn("Test payment recovery batch failed; retry on next poll");
        }
    }

    private void count(String outcome, int amount) {
        metrics.counter("payment.test.recovery.orders", "outcome", outcome).increment(amount);
    }
}
