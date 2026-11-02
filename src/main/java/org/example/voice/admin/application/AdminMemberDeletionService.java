package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.admin.domain.port.AdminMemberDeletionStore;
import org.example.voice.user.domain.port.UserReader;

import org.example.voice.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

/** Administrator-only DB erasure. Does not invoke any external media lifecycle. */
@Service @RequiredArgsConstructor
public class AdminMemberDeletionService {
    private final AdminAccessService access;
    private final AdminStore audit;
    private final UserReader users;
    private final AdminMemberDeletionStore deletion;
    private final org.example.voice.user.domain.port.UserSessionRevoker sessions;
    public record Result(long userId,OffsetDateTime deletedAt,boolean alreadyDeleted) {}

    @Transactional
    public Result delete(long actor,long targetId,long confirmationUserId,OffsetDateTime expectedUpdatedAt,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        if(targetId!=confirmationUserId) AdminValidation.invalid();
        if(actor==targetId) throw new BaseException(ErrorCode.ADMIN_SELF_DELETION_NOT_ALLOWED);
        var prior=deletion.deletedAt(targetId);
        if(prior.isPresent()) return new Result(targetId,prior.get(),true);
        var candidate=users.findByIdForUpdate(targetId);
        if(candidate.isEmpty()) return deletion.deletedAt(targetId)
                .map(at->new Result(targetId,at,true))
                .orElseThrow(()->new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        var target=candidate.get();
        boolean current=expectedUpdatedAt==null?target.getUpdatedAt()==null:target.getUpdatedAt()!=null && target.getUpdatedAt().toInstant().equals(expectedUpdatedAt.toInstant());
        if(!current) AdminValidation.conflict();
        sessions.revokeAll(targetId);
        var deletedAt=deletion.deleteDatabaseRecords(targetId);
        audit.audit(AdminAuditLog.success(actor,"USER_DELETE","USER",targetId,reason,
                "memberDatabaseRecords",deletedAt));
        audit.flush();
        return new Result(targetId,deletedAt,false);
    }
}
