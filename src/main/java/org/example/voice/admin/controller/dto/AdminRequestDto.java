package org.example.voice.admin.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.support.domain.entity.Inquiry;
import org.example.voice.support.domain.model.SupportData.Section;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminRequestDto {
    private AdminRequestDto() {}
    public record ContentCreateDto(@NotNull @Valid ContentBodyDto content, @NotBlank @Size(max=500) String reason) {}
    public record ContentEditDto(@NotNull @PositiveOrZero Long version, @NotNull @Valid ContentBodyDto content,
                                 @NotBlank @Size(max=500) String reason) {}
    public record ContentBodyDto(@NotNull ContentType type, @NotNull LearningFocus focus,
            @NotBlank @Size(max=100) String category, @NotNull Difficulty difficulty,
            @NotBlank @Size(max=255) String title, @Size(max=1000) String description,
            @NotBlank @Size(max=10000) String scriptText,
            @NotNull @Size(max=100) List<@NotBlank @Size(max=200) String> targetPronunciations,
            @NotNull @Min(1) @Max(3600) Integer estimatedSeconds) {
        public ContentDraft command() { return new ContentDraft(type,focus,category,difficulty,title,description,scriptText,targetPronunciations,estimatedSeconds); }
    }
    public record PublicationDto(@NotNull @PositiveOrZero Long version, @NotNull PublishStatus status,
                                  @NotBlank @Size(max=500) String reason) {}
    public record NoticeCreateDto(@NotNull @Valid NoticeBodyDto notice, @NotBlank @Size(max=500) String reason) {}
    public record NoticeEditDto(@NotNull @PositiveOrZero Long version, @NotNull @Valid NoticeBodyDto notice,
                                @NotBlank @Size(max=500) String reason) {}
    public record AdminSectionRequestDto(@NotBlank @Size(max=200) String title,
                              @NotEmpty @Size(max=50) List<@NotBlank @Size(max=4000) String> paragraphs) {
        public Section command() { return new Section(title,paragraphs); }
    }
    public record NoticeBodyDto(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=500) String summary,
            @NotEmpty @Size(max=30) List<@NotNull @Valid AdminSectionRequestDto> sections,
            @NotNull Boolean pinned, @NotNull Boolean published, @NotNull OffsetDateTime publishedAt) {
        public NoticeDraft command() { return new NoticeDraft(title,summary,sections.stream().map(AdminSectionRequestDto::command).toList(),pinned,published,publishedAt); }
    }
    public record AnswerDto(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=4000) String answer,
                             @NotBlank @Size(max=500) String reason) {}
    public record InquiryStatusDto(@NotNull @PositiveOrZero Long version, @NotNull Inquiry.Status status,
                                    @NotBlank @Size(max=500) String reason) {}
}
