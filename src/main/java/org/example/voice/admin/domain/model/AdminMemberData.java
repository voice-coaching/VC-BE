package org.example.voice.admin.domain.model;
import java.util.*;
import org.example.voice.admin.domain.type.AdminUserResource;
public final class AdminMemberData {
    private AdminMemberData() {}
    public record RecordView(Map<String,Object> fields) {
        public RecordView { fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields)); }
    }
    public record LoginConnection(String provider,String email,java.time.OffsetDateTime connectedAt) {}
    public record AccountDetails(String email,java.time.OffsetDateTime lastLoginAt,java.time.OffsetDateTime updatedAt,
            java.time.OffsetDateTime withdrawnAt,java.time.OffsetDateTime termsAgreedAt,java.time.OffsetDateTime privacyAgreedAt,
            List<LoginConnection> loginConnections) {}
    public record IdentifierMatch(long userId,String source) {}
    public record ResourceCount(AdminUserResource resource,long count) {}
}
