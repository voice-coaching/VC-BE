package org.example.voice.support.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public final class SupportData {
    private SupportData() {}

    public record Page<T>(List<T> items, int page, int size, long totalElements) {
        public Page { items = List.copyOf(items); }
        public int totalPages() { return (int) ((totalElements + size - 1) / size); }
        public boolean hasNext() { return ((long) page + 1) * size < totalElements; }
    }

    public record Section(String title, List<String> paragraphs) {
        public Section { paragraphs = List.copyOf(paragraphs); }
    }
    public record NoticeSummary(Long id, String title, boolean pinned, OffsetDateTime publishedAt, String summary) {}
    public record NoticeDetail(Long id, String title, boolean pinned, OffsetDateTime publishedAt,
                               String summary, List<Section> sections) {}
    public record InquiryDraft(String category, String subject, String body, Long relatedSessionId, String replyEmail) {}
    public record InquiryView(Long id, String category, String subject, String body, Long relatedSessionId,
                              String replyEmail, String status, String answer,
                              OffsetDateTime createdAt, OffsetDateTime answeredAt) {}
    public record CreatedInquiry(Long id, String status, OffsetDateTime createdAt) {}
}
