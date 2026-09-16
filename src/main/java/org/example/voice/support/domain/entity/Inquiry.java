package org.example.voice.support.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.support.domain.model.SupportData.InquiryDraft;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Table(name = "inquiries", uniqueConstraints = @UniqueConstraint(
        name = "uq_inquiries_user_idempotency", columnNames = {"user_id", "idempotency_key_sha256"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {
    public enum Status { RECEIVED, IN_PROGRESS, ANSWERED, CLOSED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 50)
    private String category;
    @Column(nullable = false, length = 100)
    private String subject;
    @Column(nullable = false, length = 2000)
    private String body;
    @Column(name = "related_session_id")
    private Long relatedSessionId;
    @Column(name = "reply_email", length = 254)
    private String replyEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(length = 4000)
    private String answer;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;
    @Column(name = "idempotency_key_sha256", length = 64)
    private String idempotencyKeySha256;
    @Column(name = "request_sha256", nullable = false, length = 64)
    private String requestSha256;

    public static Inquiry receive(Long userId, InquiryDraft draft, String keyDigest,
                                  String requestDigest, OffsetDateTime now) {
        Inquiry inquiry = new Inquiry();
        inquiry.userId = userId;
        inquiry.category = draft.category();
        inquiry.subject = draft.subject();
        inquiry.body = draft.body();
        inquiry.relatedSessionId = draft.relatedSessionId();
        inquiry.replyEmail = draft.replyEmail();
        inquiry.status = Status.RECEIVED;
        inquiry.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        inquiry.idempotencyKeySha256 = keyDigest;
        inquiry.requestSha256 = requestDigest;
        return inquiry;
    }

    public boolean matchesRequest(String digest) { return requestSha256.equals(digest); }
}
