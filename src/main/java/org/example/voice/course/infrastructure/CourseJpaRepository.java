package org.example.voice.course.infrastructure;

import org.example.voice.course.domain.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseJpaRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
}
