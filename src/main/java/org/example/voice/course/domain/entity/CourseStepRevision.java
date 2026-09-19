package org.example.voice.course.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.example.voice.course.domain.type.CourseStepType;

@Entity @Getter @Immutable @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "course_step_revisions", uniqueConstraints = @UniqueConstraint(columnNames = {"step_id", "revision"}))
public class CourseStepRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "step_id", nullable = false) private Long stepId;
    @Column(nullable = false) private Integer revision;
    @Column(nullable = false) private String title;
    private String subtitle;
    @Column(name = "step_order", nullable = false) private Integer stepOrder;
    @Enumerated(EnumType.STRING) @Column(name = "step_type", nullable = false) private CourseStepType stepType;
    @Column(name = "blocks_json", nullable = false, columnDefinition = "text") private String blocksJson;
}
