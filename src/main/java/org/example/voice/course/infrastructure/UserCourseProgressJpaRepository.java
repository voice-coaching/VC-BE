package org.example.voice.course.infrastructure;

import org.example.voice.course.domain.entity.UserCourseProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCourseProgressJpaRepository extends JpaRepository<UserCourseProgress, Long> {

    Optional<UserCourseProgress> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);
}
