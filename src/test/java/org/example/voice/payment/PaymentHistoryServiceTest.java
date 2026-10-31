package org.example.voice.payment;

import org.example.voice.payment.application.PaymentHistoryService;
import org.example.voice.payment.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentHistoryServiceTest {
    @Test void disabledOrNonAllowedUserCannotQueryHistory() {
        var orders = mock(PaymentOrderStore.class);
        var policy = mock(PaymentPolicy.class, CALLS_REAL_METHODS);
        var service = new PaymentHistoryService(orders, policy);
        assertThatThrownBy(() -> service.read(1L, 0, 20)).isInstanceOf(PaymentException.class)
                .hasMessage("TEST_PAYMENT_DISABLED");
        verifyNoInteractions(orders);
    }
}
