package org.example.voice.admin.infrastructure;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.AdminMemberDeletionStore;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.Optional;
@Repository @RequiredArgsConstructor
public class AdminMemberDeletionPersistence implements AdminMemberDeletionStore {
    private final EntityManager em;
    public Optional<OffsetDateTime> deletedAt(long id) {
        return em.createNativeQuery("select deleted_at from admin_member_deletion_receipts where user_id=:id")
                .setParameter("id",id).getResultStream().findFirst().map(this::timestamp);
    }
    public OffsetDateTime deleteDatabaseRecords(long id) {
        em.flush();
        var deleted=timestamp(em.createNativeQuery("select admin_purge_member_database(:id)").setParameter("id",id).getSingleResult());
        // Native deletes must not leave a managed member/refresh session readable from the first-level cache.
        em.clear();return deleted;
    }
    private OffsetDateTime timestamp(Object value) {
        if(value instanceof OffsetDateTime t)return t;
        if(value instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(value.toString());
    }
}
