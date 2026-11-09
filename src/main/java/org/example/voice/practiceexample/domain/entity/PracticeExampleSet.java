package org.example.voice.practiceexample.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;

@Entity @Getter @Immutable @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "practice_example_sets")
public class PracticeExampleSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = true) private Long stepId;
    @Column(nullable = true) private Integer revision;
    @Column(nullable = true) private Long educationRevisionId;
    @Column(nullable = true) private OffsetDateTime publishedAt;

    public static PracticeExampleSet published(long stepId,int revision,long educationRevisionId,OffsetDateTime now) {
        var set=new PracticeExampleSet();set.stepId=stepId;set.revision=revision;set.educationRevisionId=educationRevisionId;set.publishedAt=now;return set;
    }
}
