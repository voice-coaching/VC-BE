package org.example.voice.practiceexample.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;

@Entity @Getter @Immutable @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "practice_examples")
public class PracticeExample {
    @Id @Column(length = 100) private String id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "set_id", nullable = true) private PracticeExampleSet set;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "content_id", nullable = true, unique = false) private PracticeContent content;
    @Column(name = "example_order", nullable = true) private Integer order;
    @Column(length = 500) private String hint;
    @Enumerated(EnumType.STRING) @Column(length = 30) private LearningFocus focus;
    @Column(nullable = true, length = 10) private String locale;

    public static PracticeExample authored(PracticeExampleSet set,PracticeContent content,int order,String hint,LearningFocus focus) {
        var example=new PracticeExample();example.id="admin-"+java.util.UUID.randomUUID();example.set=set;example.content=content;
        example.order=order;example.hint=hint;example.focus=focus;example.locale="ko-KR";return example;
    }
}
