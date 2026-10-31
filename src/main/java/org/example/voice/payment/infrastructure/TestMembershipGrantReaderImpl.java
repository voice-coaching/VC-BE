package org.example.voice.payment.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.port.TestMembershipGrantReader;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TestMembershipGrantReaderImpl implements TestMembershipGrantReader {
    private final EntityManager em;

    @Override
    public boolean exists(Long userId) {
        return em.createQuery("select count(g) from TestMembershipGrant g where g.userId=:user", Long.class)
                .setParameter("user", userId).getSingleResult() > 0;
    }
}
