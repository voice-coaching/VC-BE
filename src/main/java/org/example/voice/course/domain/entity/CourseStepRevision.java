package org.example.voice.course.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.example.voice.course.domain.type.CourseStepType;

@Entity @Getter @Immutable @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "course_step_revisions")
public class CourseStepRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "step_id", nullable = true) private Long stepId;
    @Column(nullable = true) private Integer revision;
    @Column(nullable = true) private String title;
    private String subtitle;
    @Column(name = "step_order", nullable = true) private Integer stepOrder;
    @Enumerated(EnumType.STRING) @Column(name = "step_type", nullable = true) private CourseStepType stepType;
    @Column(name = "blocks_json", nullable = true, columnDefinition = "text") private String blocksJson;

    public static CourseStepRevision practice(long stepId,String title) {
        var revision=new CourseStepRevision();revision.stepId=stepId;revision.revision=1;revision.title=title;
        revision.stepOrder=1;revision.stepType=CourseStepType.PRACTICE;revision.blocksJson="[]";return revision;
    }
}
