package org.example.voice.course.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.course.domain.entity.Course;
import org.example.voice.course.domain.entity.CourseStep;
import org.example.voice.course.domain.entity.UserCourseProgress;
import org.example.voice.course.domain.model.CourseProgressData;
import org.example.voice.course.domain.port.CourseProgressWriter;
import org.example.voice.course.domain.type.CourseProgressStatus;
import org.example.voice.course.exception.CourseAlreadyCompletedException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class CourseProgressWriterImpl implements CourseProgressWriter {

    private final CourseJpaRepository courseJpaRepository;
    private final CourseStepJpaRepository courseStepJpaRepository;
    private final UserCourseProgressJpaRepository userCourseProgressJpaRepository;

    @Override
    public CourseProgressData startCourse(Long courseId, Long userId) {
        Course course = courseJpaRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        return userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .map(progress -> validateAndReturnStartedProgress(progress, courseId))
                .orElseGet(() -> createProgress(course, userId));
    }

    private CourseProgressData validateAndReturnStartedProgress(UserCourseProgress progress, Long courseId) {
        if (progress.getStatus() == CourseProgressStatus.COMPLETED) {
            throw new CourseAlreadyCompletedException();
        }
        validateLastStep(progress.getLastStepId(), courseId);
        return toData(progress);
    }

    private void validateLastStep(Long lastStepId, Long courseId) {
        if (lastStepId != null && !courseStepJpaRepository.existsByIdAndCourseId(lastStepId, courseId)) {
            throw new BaseException(ErrorCode.INVALID_COURSE_STEP);
        }
    }

    private CourseProgressData createProgress(Course course, Long userId) {
        CourseStep firstStep = courseStepJpaRepository.findFirstByCourseIdOrderByStepOrderAscIdAsc(course.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        UserCourseProgress progress = UserCourseProgress.start(course, userId, firstStep.getId(), OffsetDateTime.now());
        return toData(userCourseProgressJpaRepository.save(progress));
    }

    private CourseProgressData toData(UserCourseProgress progress) {
        return new CourseProgressData(
                progress.getCourse().getId(),
                progress.getStatus(),
                progress.getLastStepId(),
                progress.getProgressPercent().doubleValue(),
                progress.getStartedAt(),
                progress.getCompletedAt(),
                progress.getUpdatedAt()
        );
    }
}
