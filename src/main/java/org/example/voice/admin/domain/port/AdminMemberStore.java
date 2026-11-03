package org.example.voice.admin.domain.port;
import java.util.*;
import org.example.voice.admin.domain.model.AdminData.Page;
import org.example.voice.admin.domain.model.AdminMemberData.*;
import org.example.voice.admin.domain.type.AdminUserResource;
public interface AdminMemberStore {
    boolean activeMember(long userId);
    Optional<AccountDetails> accountDetails(long userId);
    Page<RecordView> records(long userId,AdminUserResource resource,int page,int size);
    List<ResourceCount> counts(long userId);
    List<IdentifierMatch> resolve(UUID identifier);
}
