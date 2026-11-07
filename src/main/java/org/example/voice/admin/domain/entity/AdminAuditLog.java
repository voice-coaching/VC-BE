package org.example.voice.admin.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Getter @Table(name = "admin_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="actor_user_id", nullable=false) private Long actorId;
    @Column(nullable=false, length=80) private String action;
    @Column(name="target_type", nullable=false, length=30) private String targetType;
    @Column(name="target_id", nullable=false, length=100) private String targetId;
    @Column(nullable=false, length=500) private String reason;
    @Column(name="changed_fields", nullable=false, length=500) private String changedFields;
    @Column(nullable=false, length=20) private String outcome;
    @Column(name="correlation_id", nullable=false, length=36) private String correlationId;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;

    public static AdminAuditLog success(long actor, String action, String type, long target, String reason,
                                        String fields, OffsetDateTime now) {
        return success(actor, action, type, Long.toString(target), reason, fields, now);
    }
    public static AdminAuditLog success(long actor, String action, String type, String target, String reason,
                                        String fields, OffsetDateTime now) {
        var log = new AdminAuditLog();
        log.actorId=actor; log.action=action; log.targetType=type; log.targetId=target;
        log.reason=reason; log.changedFields=fields; log.outcome="SUCCESS";
        log.correlationId=java.util.UUID.randomUUID().toString(); log.createdAt=now;
        return log;
    }
}
