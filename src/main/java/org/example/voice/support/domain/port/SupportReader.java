package org.example.voice.support.domain.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.example.voice.support.domain.entity.Inquiry;
import org.example.voice.support.domain.model.SupportData.*;

public interface SupportReader {
    Page<NoticeSummary> findNotices(OffsetDateTime now, int page, int size);
    Optional<NoticeDetail> findNotice(Long id, OffsetDateTime now);
    Page<InquiryView> findInquiries(Long userId, int page, int size);
    Optional<InquiryView> findInquiry(Long userId, Long inquiryId);
    Optional<Inquiry> findByIdempotencyKey(Long userId, String keyDigest);
}
