package org.example.voice.support.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.support.domain.SupportException;
import org.example.voice.support.domain.entity.Inquiry;
import org.example.voice.support.domain.model.SupportData.*;
import org.example.voice.support.domain.port.InquiryWriter;
import org.example.voice.support.domain.port.SupportReader;
import org.example.voice.training.domain.port.TrainingSessionReader;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {
    private final SupportReader reader;
    private final InquiryWriter writer;
    private final UserReader users;
    private final TrainingSessionReader sessions;
    private final Clock clock;

    public Page<NoticeSummary> notices(int page, int size) {
        validatePage(page, size);
        return reader.findNotices(OffsetDateTime.now(clock), page, size);
    }

    public NoticeDetail notice(Long id) {
        return reader.findNotice(id, OffsetDateTime.now(clock))
                .orElseThrow(() -> new SupportException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public Page<InquiryView> inquiries(Long userId, int page, int size) {
        validatePage(page, size);
        return reader.findInquiries(userId, page, size);
    }

    public InquiryView inquiry(Long userId, Long id) {
        return reader.findInquiry(userId, id)
                .orElseThrow(() -> new SupportException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public CreatedInquiry create(Long userId, InquiryDraft draft, String idempotencyKey) {
        validateDraft(draft);
        if (idempotencyKey != null && !idempotencyKey.matches("[!-~]{1,128}")) {
            throw new SupportException(ErrorCode.VALIDATION_ERROR);
        }
        // Serialize requests for this owner, including concurrent submissions of the same key.
        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new SupportException(ErrorCode.RESOURCE_NOT_FOUND));
        if (user.isWithdrawn() || user.isSuspended()) {
            throw new SupportException(ErrorCode.ACCESS_DENIED);
        }
        String keyDigest = idempotencyKey == null ? null : digest(idempotencyKey);
        String requestDigest = digest(field(draft.category()) + field(draft.subject()) + field(draft.body())
                + field(draft.relatedSessionId() == null ? null : draft.relatedSessionId().toString())
                + field(draft.replyEmail()));
        if (keyDigest != null) {
            var existing = reader.findByIdempotencyKey(userId, keyDigest);
            if (existing.isPresent()) {
                if (!existing.get().matchesRequest(requestDigest)) {
                    throw new SupportException(ErrorCode.CONFLICT);
                }
                return receipt(existing.get());
            }
        }
        if (draft.relatedSessionId() != null && !sessions.existsSession(draft.relatedSessionId(), userId)) {
            throw new SupportException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Inquiry inquiry = writer.save(Inquiry.receive(userId, draft, keyDigest, requestDigest, OffsetDateTime.now(clock)));
        return receipt(inquiry);
    }

    private static CreatedInquiry receipt(Inquiry inquiry) {
        // The creation receipt remains stable even if support later updates the inquiry status.
        return new CreatedInquiry(inquiry.getId(), Inquiry.Status.RECEIVED.name(), inquiry.getCreatedAt());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new SupportException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void validateDraft(InquiryDraft draft) {
        if (draft == null || draft.category() == null || !draft.category().matches("[A-Z][A-Z0-9_]{0,49}")
                || !validText(draft.subject(), 100) || !validText(draft.body(), 2000)
                || draft.relatedSessionId() != null && draft.relatedSessionId() <= 0) {
            throw new SupportException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static boolean validText(String value, int maximum) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= maximum
                && value.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }

    private static String field(String value) { return value == null ? "-1:" : value.length() + ":" + value; }
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
