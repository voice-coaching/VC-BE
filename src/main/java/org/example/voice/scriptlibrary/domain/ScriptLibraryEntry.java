package org.example.voice.scriptlibrary.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="script_library_entries")
public class ScriptLibraryEntry {
    @Id @Column(name="content_id") private Long contentId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="topic_id") private Long topicId;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    public ScriptLibraryEntry(Long contentId,Long userId,Long topicId,OffsetDateTime now) {
        this.contentId=contentId;this.userId=userId;this.topicId=topicId;this.createdAt=now;
    }
}
