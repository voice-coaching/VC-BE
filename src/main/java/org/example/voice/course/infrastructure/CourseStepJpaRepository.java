package org.example.voice.course.infrastructure;

import org.example.voice.course.domain.entity.CourseStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseStepJpaRepository extends JpaRepository<CourseStep, Long> {

    int countByCourseId(Long courseId);
}
