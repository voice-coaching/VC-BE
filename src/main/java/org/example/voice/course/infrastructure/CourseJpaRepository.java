package org.example.voice.course.infrastructure;

import org.example.voice.course.domain.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CourseJpaRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
    List<Course> findByStatusOrderByUpdatedAtDesc(PublishStatus status, Pageable pageable);
}
