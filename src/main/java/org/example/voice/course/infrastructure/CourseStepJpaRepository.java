package org.example.voice.course.infrastructure;

import org.example.voice.course.domain.entity.CourseStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseStepJpaRepository extends JpaRepository<CourseStep, Long> {

    int countByCourseId(Long courseId);

    int countByCourseIdAndRetiredFalse(Long courseId);

    List<CourseStep> findByCourseIdAndRetiredFalseOrderByStepOrderAscIdAsc(Long courseId);

    Optional<CourseStep> findFirstByCourseIdAndRetiredFalseOrderByStepOrderAscIdAsc(Long courseId);

    List<CourseStep> findByCourseIdOrderByStepOrderAscIdAsc(Long courseId);

    Optional<CourseStep> findFirstByCourseIdOrderByStepOrderAscIdAsc(Long courseId);

    Optional<CourseStep> findFirstByCourseIdAndRequiredTrueOrderByStepOrderDescIdDesc(Long courseId);

    Optional<CourseStep> findByIdAndCourseId(Long id, Long courseId);

    boolean existsByIdAndCourseId(Long id, Long courseId);
}
