package org.example.voice.admin.domain.port;
import java.time.OffsetDateTime;
import java.util.Optional;
public interface AdminMemberDeletionStore {
    Optional<OffsetDateTime> deletedAt(long userId);
    OffsetDateTime deleteDatabaseRecords(long userId);
}
