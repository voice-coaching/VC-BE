package org.example.voice.practicecontent.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "practice_contents")
public class PracticeContent {

    @jakarta.persistence.Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = true)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_focus", nullable = true)
    private LearningFocus learningFocus;

    @Column(name = "category")
    private String category;

    @Column(name = "title", nullable = true)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "script_text", nullable = true)
    private String scriptText;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = true)
    private Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_pronunciations", columnDefinition = "jsonb")
    private List<String> targetPronunciations;

    @Column(name = "estimated_seconds")
    private Integer estimatedSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true)
    private PublishStatus status;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = true)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private OffsetDateTime updatedAt;

    @Column(name = "publisher")
    private String publisher;

    @Column(name = "speaker_name")
    private String speakerName;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "custom_script_ciphertext", columnDefinition = "text")
    private org.example.voice.practicecontent.domain.model.PrivateText customScript;

    @Column(name = "custom_title_ciphertext", columnDefinition = "text")
    private org.example.voice.practicecontent.domain.model.PrivateText customTitle;

    @Column(name = "custom_deleted_at")
    private OffsetDateTime customDeletedAt;

    public String getScriptText() { return ownerId == null ? scriptText : customScript == null ? "" : customScript.value(); }
    public String getTitle() { return ownerId == null ? title : customTitle == null ? "삭제된 내 문장" : customTitle.value(); }

    public static PracticeContent custom(Long userId, String title, String script, LearningFocus focus, OffsetDateTime now) {
        PracticeContent content = new PracticeContent();
        content.ownerId=userId; content.contentType=ContentType.SENTENCE; content.learningFocus=focus;
        content.category="CUSTOM"; content.title="내 문장"; content.scriptText="[private]";
        content.customTitle=new org.example.voice.practicecontent.domain.model.PrivateText(title);
        content.customScript=new org.example.voice.practicecontent.domain.model.PrivateText(script);
        content.description="직접 입력한 문장으로 연습해요."; content.difficulty=Difficulty.INTERMEDIATE;
        content.targetPronunciations=List.of(); content.estimatedSeconds=Math.max(1,(script.codePointCount(0,script.length())+4)/5);
        content.status=PublishStatus.HIDDEN; content.createdAt=now; content.updatedAt=now;
        return content;
    }

    public void eraseCustom(OffsetDateTime now) {

        customTitle=null; customScript=null; customDeletedAt=now; updatedAt=now;
    }

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "tts_revision", nullable = false, columnDefinition = "integer default 1")
    private int ttsRevision = 1;

    public void delete(OffsetDateTime now) {
        if (ownerId != null || deletedAt != null) throw new IllegalStateException("CONTENT_NOT_EDITABLE");
        deletedAt = now; status = PublishStatus.HIDDEN; updatedAt = now;
    }

    /** Published public content remains editable; private ownership is a separate boundary. */
    public void editPublic(LearningFocus focus, String category, Difficulty difficulty, String title,
            String description, String script, List<String> targets, Integer seconds, OffsetDateTime now) {
        if (ownerId != null || deletedAt != null) throw new IllegalStateException("CONTENT_NOT_EDITABLE");
        if (!java.util.Objects.equals(scriptText, script)) ttsRevision++;
        this.learningFocus = focus; this.category = category; this.difficulty = difficulty;
        this.title = title; this.description = description; this.scriptText = script;
        this.targetPronunciations = List.copyOf(targets); this.estimatedSeconds = seconds; this.updatedAt = now;
    }

    public boolean isPublished() {
        return ownerId == null && deletedAt == null && status == PublishStatus.PUBLISHED;
    }

    public static PracticeContent draft(ContentType type, LearningFocus focus, String category, Difficulty difficulty,
            String title, String description, String script, List<String> targets, Integer seconds, OffsetDateTime now) {
        var content = new PracticeContent();
        content.contentType = type;
        content.status = PublishStatus.DRAFT;
        content.createdAt = now;
        content.editDraft(focus, category, difficulty, title, description, script, targets, seconds, now);
        return content;
    }

    public void editDraft(LearningFocus focus, String category, Difficulty difficulty, String title,
            String description, String script, List<String> targets, Integer seconds, OffsetDateTime now) {
        if (ownerId != null || status != PublishStatus.DRAFT || publishedAt != null)
            throw new IllegalStateException("PUBLISHED_CONTENT_IMMUTABLE");
        this.learningFocus = focus; this.category = category; this.difficulty = difficulty;
        this.title = title; this.description = description; this.scriptText = script;
        this.targetPronunciations = List.copyOf(targets); this.estimatedSeconds = seconds; this.updatedAt = now;
    }

    public void changePublication(PublishStatus target, OffsetDateTime now) {
        if (ownerId != null || deletedAt != null || target == PublishStatus.DRAFT || target == null
                || (target == PublishStatus.HIDDEN && status == PublishStatus.DRAFT))
            throw new IllegalStateException("INVALID_PUBLICATION_STATE");
        status = target;
        if (target == PublishStatus.PUBLISHED && publishedAt == null) publishedAt = now;
        updatedAt = now;
    }

}
