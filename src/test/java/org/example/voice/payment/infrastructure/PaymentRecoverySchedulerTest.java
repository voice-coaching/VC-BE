package org.example.voice.payment.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.payment.application.PaymentRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRecoverySchedulerTest {
    @Test void bothFlagsAreRequiredToCreateScheduler() {
        var runner = new ApplicationContextRunner()
                .withBean(PaymentRecoveryService.class, () -> mock(PaymentRecoveryService.class))
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withUserConfiguration(PaymentRecoveryScheduler.class);
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(PaymentRecoveryScheduler.class));
        runner.withPropertyValues("payment.test.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PaymentRecoveryScheduler.class));
        runner.withPropertyValues("payment.test.recovery.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PaymentRecoveryScheduler.class));
        runner.withPropertyValues("payment.test.enabled=true", "payment.test.recovery.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(PaymentRecoveryScheduler.class));
    }

    @Test void failedBatchIsObservableAndNextPollStillRuns() {
        var recovery = mock(PaymentRecoveryService.class);
        var metrics = new SimpleMeterRegistry();
        var scheduler = new PaymentRecoveryScheduler(recovery, metrics);
        when(recovery.recover()).thenThrow(new IllegalStateException("private details"))
                .thenReturn(new PaymentRecoveryService.Result(2, 1, 1, 1));
        scheduler.poll();
        scheduler.poll();
        assertThat(metrics.get("payment.test.recovery.batch.failures").counter().count()).isEqualTo(1);
        assertThat(metrics.get("payment.test.recovery.orders").tag("outcome", "applied").counter().count()).isEqualTo(2);
        assertThat(metrics.get("payment.test.recovery.orders").tag("outcome", "failed").counter().count()).isEqualTo(1);
    }
}
