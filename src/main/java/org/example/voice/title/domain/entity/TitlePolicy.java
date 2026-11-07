package org.example.voice.title.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.title.domain.TitleRank;

@Entity @Table(name = "title_policies") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitlePolicy {
    @Id @Enumerated(EnumType.STRING) @Column(name = "target_rank", length = 30) private TitleRank targetRank;
    @Column(name = "required_training_count", nullable = true) private long requiredTrainingCount;
    @Column(name = "passing_score", nullable = true) private int passingScore;
    @Column(name = "practice_content_id") private Long practiceContentId;
    @Column(name = "edit_version", nullable = false) private long editVersion;

    public void configure(Long contentId, int score) {
        if (contentId == null || contentId <= 0 || score < 1 || score > 100)
            throw new IllegalArgumentException("Invalid title exam policy");
        practiceContentId = contentId;
        passingScore = score;
        editVersion++;
    }
}
