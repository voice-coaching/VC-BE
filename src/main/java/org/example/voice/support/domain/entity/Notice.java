package org.example.voice.support.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.support.domain.model.SupportData.Section;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Table(name = "notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {
    @Version @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = true, length = 200)
    private String title;
    @Column(nullable = true, length = 500)
    private String summary;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = true, columnDefinition = "jsonb")
    private List<Section> sections;
    @Column(nullable = true)
    private boolean pinned;
    @Column(nullable = true)
    private boolean published;
    @Column(name = "published_at", nullable = true)
    private OffsetDateTime publishedAt;

    public static Notice scheduled(String title, String summary, List<Section> sections,
                                   boolean pinned, OffsetDateTime publishedAt) {
        Notice notice = new Notice();
        notice.title = Objects.requireNonNull(title);
        notice.summary = Objects.requireNonNull(summary);
        notice.sections = List.copyOf(sections);
        notice.pinned = pinned;
        notice.publishedAt = Objects.requireNonNull(publishedAt);
        notice.published = true;
        return notice;
    }

    public void hide() { published = false; }

    public void edit(String title, String summary, List<Section> sections,
                     boolean pinned, boolean published, OffsetDateTime publishedAt) {
        this.title = Objects.requireNonNull(title);
        this.summary = Objects.requireNonNull(summary);
        this.sections = List.copyOf(sections);
        this.pinned = pinned; this.published = published;
        this.publishedAt = Objects.requireNonNull(publishedAt);
    }
}
