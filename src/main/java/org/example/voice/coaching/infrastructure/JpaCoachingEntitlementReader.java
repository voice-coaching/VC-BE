package org.example.voice.coaching.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.coaching.domain.CoachingWindow;
import org.example.voice.coaching.domain.port.CoachingEntitlementReader;
import org.example.voice.payment.domain.PaymentOrder;
import org.example.voice.payment.domain.PaymentPolicy;
import org.example.voice.payment.domain.port.TestMembershipGrantReader;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.Optional;

/** Adapts existing TEST grants only; no real billing or automatic renewal is introduced. */
@Repository
@RequiredArgsConstructor
public class JpaCoachingEntitlementReader implements CoachingEntitlementReader {
    private final EntityManager em;
    private final PaymentPolicy policy;
    private final TestMembershipGrantReader testGrants;

    @Override public Optional<CoachingWindow> find(Long userId, OffsetDateTime now) {
        if (testGrants.exists(userId)) return Optional.of(CoachingWindow.admin());
        if (!policy.eligible(userId)) return Optional.empty();
        return em.createQuery("""
                select p from PaymentOrder p where p.userId=:user and p.status='PAID'
                and p.paidAt<=:now and p.expiresAt>:now order by p.expiresAt desc, p.id desc
                """, PaymentOrder.class).setParameter("user", userId).setParameter("now", now)
                .setMaxResults(1).getResultStream().findFirst()
                .map(p -> new CoachingWindow("paid:" + p.getId(), "paid", "TEST",
                        p.getPaidAt(), p.getExpiresAt(), 30));
    }
}
