package org.example.voice.admin.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.user.domain.type.*;
import org.example.voice.support.domain.entity.Inquiry;
import org.example.voice.support.domain.model.SupportData.Section;

public final class AdminData {
    private AdminData() {}
    public record Page<T>(List<T> items, int page, int size, long totalElements) {
        public Page { items = List.copyOf(items); }
        public int totalPages() { return (int)((totalElements + size - 1) / size); }
        public boolean hasNext() { return ((long)page + 1) * size < totalElements; }
    }
    public record Query(String keyword, int page, int size) {}
    public record UserFilter(Query query, UserStatus status, UserRole role) {}
    public record ContentFilter(Query query, ContentType type, String category, PublishStatus status, Difficulty difficulty) {}
    public record NoticeFilter(Query query, Boolean published) {}
    public record InquiryFilter(Query query, Inquiry.Status status, String category) {}
    public record AuditFilter(int page, int size, Long actorId, String action, String targetType,
                              String targetId, OffsetDateTime from, OffsetDateTime to) {}
    public record Access(long id, String nickname, String role) {}
    public record UserView(long id, String email, String nickname, String status, String role, OffsetDateTime createdAt) {}
    public record ContentDeletion(long id,long version,OffsetDateTime deletedAt) {}
    public record ContentView(long id, long version, ContentType type, LearningFocus focus, String category,
                              Difficulty difficulty, String title, String description, String scriptText,
                              List<String> targetPronunciations, Integer estimatedSeconds,
                              PublishStatus status, OffsetDateTime publishedAt, OffsetDateTime updatedAt) {}
    public record Category(ContentType type, String code, String label, int sortOrder) {}
    public record NoticeView(long id, long version, String title, String summary, List<Section> sections,
                             boolean pinned, boolean published, OffsetDateTime publishedAt) {}
    public record InquiryView(long id, long version, long userId, String category, String subject, String body,
                              Long relatedSessionId, String replyEmail, String status, String answer,
                              OffsetDateTime createdAt, OffsetDateTime answeredAt) {}
    public record AuditView(long id, long actorId, String action, String targetType, String targetId,
                            String reason, String changedFields, String outcome, String correlationId, OffsetDateTime createdAt) {}
    public record ContentDraft(ContentType type, LearningFocus focus, String category, Difficulty difficulty,
                               String title, String description, String scriptText, List<String> targets, Integer seconds) {}
    public record NoticeDraft(String title, String summary, List<Section> sections,
                              boolean pinned, boolean published, OffsetDateTime publishedAt) {}
}
