package org.example.voice.payment.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.PaymentOperation;
import org.example.voice.payment.domain.entity.PaymentQuota;
import org.example.voice.payment.domain.port.PaymentRequestQuota;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserStatus;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class JpaPaymentRequestQuota implements PaymentRequestQuota {
    private final EntityManager em;
    private final Clock clock;

    @Override
    public long consume(Long userId, PaymentOperation operation) {
        // A stable parent lock also serializes the first insert across application instances.
        // No provider call or order transaction runs while this lock is held.
        User user = em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 2000));
        // Preserve downstream ownership/inactive errors without creating orphan quota rows.
        if (user == null || user.getStatus() != UserStatus.ACTIVE) return 0;
        var now = clock.instant();
        var quota = em.find(PaymentQuota.class, PaymentQuota.id(userId, operation));
        if (quota == null) {
            quota = new PaymentQuota(userId, operation, now);
            em.persist(quota);
        }
        return quota.consume(now);
    }
}
