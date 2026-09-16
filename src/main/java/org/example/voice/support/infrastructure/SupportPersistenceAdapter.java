package org.example.voice.support.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.support.domain.entity.Inquiry;
import org.example.voice.support.domain.model.SupportData.*;
import org.example.voice.support.domain.port.InquiryWriter;
import org.example.voice.support.domain.port.SupportReader;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SupportPersistenceAdapter implements SupportReader, InquiryWriter {
    private final NoticeJpaRepository notices;
    private final InquiryJpaRepository inquiries;

    public Page<NoticeSummary> findNotices(OffsetDateTime now, int page, int size) {
        var found = notices.findByPublishedTrueAndPublishedAtLessThanEqual(now,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "pinned", "publishedAt", "id")));
        return new Page<>(found.getContent().stream().map(n -> new NoticeSummary(
                n.getId(), n.getTitle(), n.isPinned(), utc(n.getPublishedAt()), n.getSummary())).toList(),
                page, size, found.getTotalElements());
    }

    public Optional<NoticeDetail> findNotice(Long id, OffsetDateTime now) {
        return notices.findByIdAndPublishedTrueAndPublishedAtLessThanEqual(id, now)
                .map(n -> new NoticeDetail(n.getId(), n.getTitle(), n.isPinned(), utc(n.getPublishedAt()),
                        n.getSummary(), n.getSections()));
    }

    public Page<InquiryView> findInquiries(Long userId, int page, int size) {
        var found = inquiries.findByUserId(userId, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new Page<>(found.getContent().stream().map(SupportPersistenceAdapter::view).toList(),
                page, size, found.getTotalElements());
    }

    public Optional<InquiryView> findInquiry(Long userId, Long id) {
        return inquiries.findByIdAndUserId(id, userId).map(SupportPersistenceAdapter::view);
    }

    public Optional<Inquiry> findByIdempotencyKey(Long userId, String keyDigest) {
        return inquiries.findByUserIdAndIdempotencyKeySha256(userId, keyDigest);
    }

    public Inquiry save(Inquiry inquiry) { return inquiries.save(inquiry); }

    private static InquiryView view(Inquiry i) {
        return new InquiryView(i.getId(), i.getCategory(), i.getSubject(), i.getBody(), i.getRelatedSessionId(),
                i.getReplyEmail(), i.getStatus().name(), i.getAnswer(), utc(i.getCreatedAt()), utc(i.getAnsweredAt()));
    }
    private static OffsetDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
