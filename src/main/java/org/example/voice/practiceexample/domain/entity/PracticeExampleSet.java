package org.example.voice.practiceexample.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;

@Entity @Getter @Immutable @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "practice_example_sets", uniqueConstraints = @UniqueConstraint(columnNames = {"step_id", "revision"}))
public class PracticeExampleSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long stepId;
    @Column(nullable = false) private Integer revision;
    @Column(nullable = false) private Long educationRevisionId;
    @Column(nullable = false) private OffsetDateTime publishedAt;
}
