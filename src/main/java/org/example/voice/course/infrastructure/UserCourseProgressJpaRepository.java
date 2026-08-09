package org.example.voice.course.infrastructure;

import org.example.voice.course.domain.entity.UserCourseProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCourseProgressJpaRepository extends JpaRepository<UserCourseProgress, Long> {

    Optional<UserCourseProgress> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    List<UserCourseProgress> findByUserIdAndCourseIdIn(Long userId, Collection<Long> courseIds);

    Optional<UserCourseProgress> findByUserIdAndCourseId(Long userId, Long courseId);
}
