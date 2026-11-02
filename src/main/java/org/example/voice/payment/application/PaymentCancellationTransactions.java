package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.PaymentException;
import org.example.voice.payment.domain.PaymentOrderStore;
import org.example.voice.payment.domain.PaymentPolicy;
import org.example.voice.payment.domain.entity.PaymentCancellation;
import org.example.voice.payment.domain.model.PaymentCancellationData;
import org.example.voice.payment.domain.port.PaymentCancellationStore;
import org.example.voice.payment.domain.type.PaymentCancellationStatus;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentCancellationTransactions {
    private final PaymentOrderStore orders;
    private final PaymentCancellationStore cancellations;
    private final PaymentPolicy policy;
    private final UserReader users;
    private final Clock clock;

    public record Reservation(PaymentCancellationData request, boolean send) {}

    @Transactional(readOnly = true)
    public Optional<PaymentCancellationData> existingRequest(Long userId, String orderId, String reason) { return Optional.empty(); }

    @Transactional(readOnly = true)
    public PaymentCancellationData read(Long userId, String requestId) {

        var request = cancellations.byId(requestId)
                .orElseThrow(() -> new PaymentException(404, "PAYMENT_CANCELLATION_NOT_FOUND"));
        var order = orders.find(request.getOrderId())
                .orElseThrow(() -> new PaymentException(404, "PAYMENT_CANCELLATION_NOT_FOUND"));

        return PaymentCancellationData.from(request);
    }

    @Transactional(readOnly = true)
    public void assertActive(Long userId) {}

    @Transactional
    public Reservation reserve(Long userId, String orderId, String reason, boolean alreadyCancelled) {

        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new PaymentException(403, "PAYMENT_USER_INACTIVE"));

        var order = orders.lock(orderId).orElseThrow(() -> new PaymentException(404, "PAYMENT_ORDER_NOT_FOUND"));

        var existing = cancellations.byOrder(orderId);

        var request = PaymentCancellation.create(orderId, reason, OffsetDateTime.now(clock));
        if (alreadyCancelled) request.record(PaymentCancellationStatus.SUCCEEDED, OffsetDateTime.now(clock));
        cancellations.save(request);
        return new Reservation(PaymentCancellationData.from(request), true);
    }

    @Transactional
    public PaymentCancellationData record(Long userId, String orderId, PaymentCancellationStatus status) {

        users.findByIdForUpdate(userId).orElseThrow(() -> new PaymentException(404, "PAYMENT_ORDER_NOT_FOUND"));
        var order = orders.lock(orderId).orElseThrow(() -> new PaymentException(404, "PAYMENT_ORDER_NOT_FOUND"));

        var request = cancellations.byOrder(orderId)
                .orElseThrow(() -> new PaymentException(404, "PAYMENT_CANCELLATION_NOT_FOUND"));
        request.record(status, OffsetDateTime.now(clock));
        return PaymentCancellationData.from(request);
    }
}
