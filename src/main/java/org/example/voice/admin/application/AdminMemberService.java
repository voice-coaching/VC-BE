package org.example.voice.admin.application;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.example.voice.admin.domain.model.AdminData.Page;
import org.example.voice.admin.domain.model.AdminMemberData.*;
import org.example.voice.admin.domain.port.AdminMemberStore;
import org.example.voice.admin.domain.type.AdminUserResource;
import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.coaching.domain.CoachingUsage;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminMemberService {
    private final AdminAccessService access;
    private final AdminReadService reads;
    private final AdminMemberStore store;
    private final CoachingUsageService coaching;
    public record Overview(AdminReadService.Member member,List<ResourceCount> counts,CoachingUsage coachingUsage,String usageUnavailableReason,AccountDetails account) {}
    public Overview overview(long actor,long userId) {
        var member=reads.user(actor,userId);boolean active=store.activeMember(userId);
        return new Overview(member,store.counts(userId),active?coaching.getUsage(userId):null,active?null:"ACCOUNT_INACTIVE",store.accountDetails(userId).orElse(null));
    }
    public Page<RecordView> records(long actor,long userId,AdminUserResource resource,int page,int size) {
        reads.user(actor,userId);AdminValidation.page(page,size);if(resource==null)AdminValidation.invalid();
        return store.records(userId,resource,page,size);
    }
    public List<IdentifierMatch> resolve(long actor,String identifier) {
        access.require(actor);AdminValidation.text(identifier,36,true);
        if(identifier.matches("[1-9][0-9]{0,18}")) {
            try { long id=Long.parseLong(identifier);reads.user(actor,id);return List.of(new IdentifierMatch(id,"USER_ID")); }
            catch(NumberFormatException e) { AdminValidation.invalid(); }
        }
        try {
            UUID id=UUID.fromString(identifier);
            if(!id.toString().equalsIgnoreCase(identifier))AdminValidation.invalid();
            return store.resolve(id);
        } catch(IllegalArgumentException e) { AdminValidation.invalid();return List.of(); }
    }
}
