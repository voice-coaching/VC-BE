package org.example.voice.practiceexample.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;

@Entity @Getter @Immutable @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "practice_examples", uniqueConstraints = @UniqueConstraint(columnNames = {"set_id", "example_order"}))
public class PracticeExample {
    @Id @Column(length = 100) private String id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "set_id", nullable = false) private PracticeExampleSet set;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "content_id", nullable = false, unique = true) private PracticeContent content;
    @Column(name = "example_order", nullable = false) private Integer order;
    @Column(length = 500) private String hint;
    @Enumerated(EnumType.STRING) @Column(length = 30) private LearningFocus focus;
    @Column(nullable = false, length = 10) private String locale;
}
