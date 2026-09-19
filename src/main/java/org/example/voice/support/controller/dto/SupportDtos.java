package org.example.voice.support.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.example.voice.support.domain.model.SupportData;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

public final class SupportDtos {
    private SupportDtos() {}
    public record CreateInquiry(
            @Schema(description = "문의 분류 코드", example = "ANALYSIS", pattern = "[A-Z][A-Z0-9_]{0,49}", requiredMode = Schema.RequiredMode.REQUIRED) String category,
            @Schema(description = "문의 제목, 공백만 허용하지 않음", minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) String subject,
            @Schema(description = "문의 본문, 공백만 허용하지 않음", minLength = 1, maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED) String body,
            @Schema(description = "본인 소유 관련 학습 세션 ID", minimum = "1") Long relatedSessionId,
                                @Email @Size(max = 254) String replyEmail) {
        public SupportData.InquiryDraft toDraft() {
            return new SupportData.InquiryDraft(category, subject, body, relatedSessionId, replyEmail);
        }
    }
    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
        public static <S,T> Page<T> from(SupportData.Page<S> data, Function<S,T> mapper) {
            return new Page<>(data.items().stream().map(mapper).toList(), data.page(), data.size(),
                    data.totalElements(), data.totalPages(), data.hasNext());
        }
    }
    public record NoticeSummary(Long id, String title, boolean pinned, OffsetDateTime publishedAt, String summary) {
        public static NoticeSummary from(SupportData.NoticeSummary n) {
            return new NoticeSummary(n.id(), n.title(), n.pinned(), n.publishedAt(), n.summary());
        }
    }
    public record Section(String title, List<String> paragraphs) {}
    public record NoticeDetail(Long id, String title, boolean pinned, OffsetDateTime publishedAt,
                               String summary, List<Section> sections) {
        public static NoticeDetail from(SupportData.NoticeDetail n) {
            return new NoticeDetail(n.id(), n.title(), n.pinned(), n.publishedAt(), n.summary(),
                    n.sections().stream().map(s -> new Section(s.title(), s.paragraphs())).toList());
        }
    }
    public record Inquiry(Long id, String category, String subject, String body, Long relatedSessionId,
                          String replyEmail,
                          @Schema(allowableValues = {"RECEIVED", "IN_PROGRESS", "ANSWERED", "CLOSED"}) String status, String answer,
                          OffsetDateTime createdAt, OffsetDateTime answeredAt) {
        public static Inquiry from(SupportData.InquiryView i) {
            return new Inquiry(i.id(), i.category(), i.subject(), i.body(), i.relatedSessionId(), i.replyEmail(),
                    i.status(), i.answer(), i.createdAt(), i.answeredAt());
        }
    }
    public record CreatedInquiry(Long id, @Schema(allowableValues = "RECEIVED") String status, OffsetDateTime createdAt) {
        public static CreatedInquiry from(SupportData.CreatedInquiry i) {
            return new CreatedInquiry(i.id(), i.status(), i.createdAt());
        }
    }
}
