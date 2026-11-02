package org.example.voice.admin.domain.model;
import java.util.*;
import org.example.voice.admin.domain.type.AdminUserResource;
public final class AdminMemberData {
    private AdminMemberData() {}
    public record RecordView(Map<String,Object> fields) {
        public RecordView { fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields)); }
    }
    public record IdentifierMatch(long userId,String source) {}
    public record ResourceCount(AdminUserResource resource,long count) {}
}
