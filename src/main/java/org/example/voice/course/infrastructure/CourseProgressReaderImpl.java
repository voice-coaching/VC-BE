package org.example.voice.course.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.controller.dto.CourseProgressSearchConditionDto;
import org.example.voice.course.domain.entity.UserCourseProgress;
import org.example.voice.course.domain.model.CourseProgressData;
import org.example.voice.course.domain.model.CourseProgressItemData;
import org.example.voice.course.domain.model.CourseProgressListData;
import org.example.voice.course.domain.port.CourseProgressReader;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseProgressReaderImpl implements CourseProgressReader {

    private final UserCourseProgressJpaRepository userCourseProgressJpaRepository;
    private final CourseStepJpaRepository courseStepJpaRepository;

    @Override
    public CourseProgressListData findMyCourseProgress(Long userId, CourseProgressSearchConditionDto condition) {
        List<UserCourseProgress> progressList = condition.status() == null
                ? userCourseProgressJpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                : userCourseProgressJpaRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, condition.status());

        List<CourseProgressItemData> items = progressList.stream()
                .map(this::toItemData)
                .toList();
        return new CourseProgressListData(items);
    }

    @Override
    public Optional<CourseProgressData> findCourseProgress(Long courseId, Long userId) {
        return userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .map(this::toData);
    }

    private CourseProgressItemData toItemData(UserCourseProgress progress) {
        return new CourseProgressItemData(
                progress.getCourse().getId(),
                progress.getCourse().getTitle(),
                progress.getStatus(),
                progress.getLastStepId(),
                progress.getProgressPercent().doubleValue(),
                progress.getUpdatedAt()
        );
    }

    private CourseProgressData toData(UserCourseProgress progress) {
        Long courseId = progress.getCourse().getId();
        return new CourseProgressData(
                courseId,
                progress.getStatus(),
                validLastStepId(progress.getLastStepId(), courseId),
                progress.getProgressPercent().doubleValue(),
                progress.getStartedAt(),
                progress.getCompletedAt(),
                progress.getUpdatedAt()
        );
    }

    private Long validLastStepId(Long lastStepId, Long courseId) {
        if (lastStepId == null) {
            return null;
        }
        return courseStepJpaRepository.existsByIdAndCourseId(lastStepId, courseId) ? lastStepId : null;
    }
}
