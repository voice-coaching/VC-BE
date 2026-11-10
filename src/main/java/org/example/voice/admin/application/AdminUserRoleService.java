package org.example.voice.admin.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.admin.domain.port.AdminUserRoleStore;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.type.UserRole;
import org.example.voice.user.domain.type.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminUserRoleService {
    private final AdminAccessService access;
    private final AdminUserRoleStore users;
    private final AdminStore audit;
    private final Clock clock;
    public record Result(long userId,UserRole role) {}

    @Transactional
    public Result change(long actor,long target,UserRole expected,UserRole role,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        if(expected==null || role==null)AdminValidation.invalid();
        var user=users.lock(actor,target);
        // Recheck after both locks: an administrator revoked concurrently cannot grant privileges.
        access.require(actor);
        if(user.getDeletedAt()!=null || user.getStatus()==UserStatus.WITHDRAWN)
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        if(actor==target || user.getRole()!=expected || user.getStatus()!=UserStatus.ACTIVE)
            AdminValidation.conflict();
        if(user.getRole()==role)return new Result(target,role);
        var now=OffsetDateTime.now(clock);
        user.changeRole(role,now);
        audit.audit(AdminAuditLog.success(actor,"USER_ROLE_CHANGE","USER",target,reason,"role",now));
        audit.flush();return new Result(target,role);
    }
}
