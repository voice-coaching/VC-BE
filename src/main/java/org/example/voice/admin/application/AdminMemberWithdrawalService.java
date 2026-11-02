package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.user.application.UserService;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.type.UserStatus;
import org.example.voice.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

/** Administrative withdrawal reuses the existing identity revocation and privacy cleanup transaction. */
@Service @RequiredArgsConstructor
public class AdminMemberWithdrawalService {
    private final AdminAccessService access;
    private final AdminStore audit;
    private final UserReader users;
    private final UserService withdrawal;
    public record Result(long userId,OffsetDateTime withdrawnAt,boolean alreadyWithdrawn) {}

    @Transactional
    public Result withdraw(long actor,long targetId,long confirmationUserId,OffsetDateTime expectedUpdatedAt,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        if(targetId!=confirmationUserId) AdminValidation.invalid();
        if(actor==targetId) throw new BaseException(ErrorCode.ADMIN_SELF_WITHDRAWAL_NOT_ALLOWED);
        var target=users.findByIdForUpdate(targetId).orElseThrow(()->new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        if(target.getStatus()==UserStatus.WITHDRAWN) return new Result(targetId,target.getDeletedAt(),true);
        boolean current=expectedUpdatedAt==null?target.getUpdatedAt()==null:target.getUpdatedAt()!=null && target.getUpdatedAt().toInstant().equals(expectedUpdatedAt.toInstant());
        if(!current) AdminValidation.conflict();
        var result=withdrawal.withdraw(targetId);
        audit.audit(AdminAuditLog.success(actor,"USER_WITHDRAW","USER",targetId,reason,
                "status,email,nickname,profileImageUrl,password,loginConnections,sessions,personalContents,notifications",result.withdrawnAt()));
        audit.flush();
        return new Result(targetId,result.withdrawnAt(),false);
    }
}
