package org.example.voice.title.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.TitleException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity @Table(name = "user_titles") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTitle {
    @Id @Column(name = "user_id") private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TitleRank rank;
    @Column(name = "minimum_training_count", nullable = false) private long minimumTrainingCount;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    public static UserTitle initial(Long userId, OffsetDateTime now) {
        var title = new UserTitle(); title.userId = userId; title.rank = TitleRank.ABSOLUTE_BEGINNER;
        title.updatedAt = now.truncatedTo(ChronoUnit.MICROS); return title;
    }
    public void promote(TitleRank target, long required, OffsetDateTime now) {
        if (rank.next() != target) throw new TitleException(409, "CONFLICT");
        rank = target; minimumTrainingCount = required; updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
}
