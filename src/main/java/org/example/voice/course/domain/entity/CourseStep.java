package org.example.voice.course.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.course.domain.type.CourseStepType;
import org.example.voice.practicecontent.domain.entity.PracticeContent;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "course_steps")
public class CourseStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = true)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_content_id")
    private PracticeContent practiceContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = true)
    private CourseStepType stepType;

    @Column(name = "step_order", nullable = true)
    private Integer stepOrder;

    @Column(name = "title", nullable = true)
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "required", nullable = true)
    private Boolean required;

    @Column(name = "retired", nullable = true)
    private boolean retired;

    public static CourseStep practice(Course course,String title) {
        var step=new CourseStep();step.course=course;step.title=title;step.stepType=CourseStepType.PRACTICE;step.stepOrder=1;step.required=true;return step;
    }
}
