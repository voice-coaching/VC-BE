package org.example.voice.payment.infrastructure;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.port.MembershipFactsReader;
import org.example.voice.payment.domain.model.MembershipFact;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
@Repository @RequiredArgsConstructor
public class JpaMembershipFactsReader implements MembershipFactsReader {
    private final EntityManager em;
    @SuppressWarnings("unchecked") public List<MembershipFact> readAll(Collection<Long> ids,OffsetDateTime now) {
        if(ids.isEmpty())return List.of();
        List<Object[]> rows=em.createNativeQuery("""
            select u.id,u.status,u.deleted_at,case when g.user_id is null then false else true end,
              (select max(p.expires_at) from payment_test_orders p where p.user_id=u.id and p.status='PAID' and p.paid_at<=:now and p.expires_at>:now)
            from users u left join user_test_membership_grants g on g.user_id=u.id where u.id in :ids
            """).setParameter("ids",ids).setParameter("now",now).getResultList();
        return rows.stream().map(r->new MembershipFact(((Number)r[0]).longValue(),"ACTIVE".equals(r[1])&&r[2]==null,Boolean.TRUE.equals(r[3]),time(r[4]))).toList();
    }
    private OffsetDateTime time(Object value) {
        if(value==null)return null;if(value instanceof OffsetDateTime at)return at;
        if(value instanceof java.sql.Timestamp at)return at.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(value.toString());
    }
}
