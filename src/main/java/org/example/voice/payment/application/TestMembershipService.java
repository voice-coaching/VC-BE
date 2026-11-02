package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.*;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TestMembershipService implements MembershipReader {
    private final PaymentPolicy policy;
    private final PaymentOrderStore orders;
    private final UserReader users;
    private final Clock clock;
    private final org.example.voice.payment.domain.port.TestMembershipGrantReader testGrants;
    private final org.example.voice.payment.domain.port.MembershipFactsReader facts;
    @Override @Transactional(readOnly = true)
    public java.util.Map<Long,Membership> readAll(java.util.Collection<Long> ids) {
        var result=new java.util.LinkedHashMap<Long,Membership>();
        ids.forEach(id->result.put(id,Membership.free()));
        if(ids.isEmpty())return result;
        for(var fact:facts.readAll(ids,OffsetDateTime.now(clock))) {
            var membership=!fact.active()?Membership.free():fact.testGrant()?Membership.testAdmin():
                    policy.eligible(fact.userId())&&fact.paidUntil()!=null?Membership.testPaid(fact.paidUntil()):Membership.free();
            result.put(fact.userId(),membership);
        }
        return result;
    }
    @Override @Transactional(readOnly = true)
    public Membership read(Long userId) {
        var user = users.findById(userId).orElse(null);
        if (user == null || user.isWithdrawn() || user.isSuspended()) return Membership.free();
        if (testGrants.exists(userId)) return Membership.testAdmin();
        if (!policy.eligible(userId)) return Membership.free();
        return orders.activeUntil(userId, OffsetDateTime.now(clock))
                .map(Membership::testPaid).orElseGet(Membership::free);
    }
}
