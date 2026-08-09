package org.example.voice.course.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.course.controller.dto.CourseProgressUpdateRequestDto;
import org.example.voice.course.domain.entity.Course;
import org.example.voice.course.domain.entity.CourseStep;
import org.example.voice.course.domain.entity.UserCourseProgress;
import org.example.voice.course.domain.model.CourseProgressData;
import org.example.voice.course.domain.port.CourseProgressWriter;
import org.example.voice.course.domain.type.CourseProgressStatus;
import org.example.voice.course.exception.CourseAlreadyCompletedException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Override
    public CourseProgressData updateCourseProgress(Long courseId, Long userId, CourseProgressUpdateRequestDto request) {
        validateProgressPercent(request);
        validateLastStep(request.lastStepId(), courseId);

        UserCourseProgress progress = userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        progress.updateProgress(request.lastStepId(), BigDecimal.valueOf(request.progressPercent()), OffsetDateTime.now());
        return toData(progress);
    }

    @Override
    public CourseProgressData completeCourse(Long courseId, Long userId) {
        UserCourseProgress progress = userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        validateRequiredStepsCompleted(progress, courseId);
        progress.complete(OffsetDateTime.now());
        return toData(progress);
    }

    private void validateProgressPercent(CourseProgressUpdateRequestDto request) {
        if (request == null || request.progressPercent() == null
                || request.progressPercent() < 0.0 || request.progressPercent() > 100.0) {
            throw new BaseException(ErrorCode.INVALID_PROGRESS_PERCENT);
        }
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

    private void validateRequiredStepsCompleted(UserCourseProgress progress, Long courseId) {
        CourseStep lastRequiredStep = courseStepJpaRepository.findFirstByCourseIdAndRequiredTrueOrderByStepOrderDescIdDesc(courseId)
                .orElse(null);
        if (lastRequiredStep == null) {
            return;
        }

        CourseStep currentStep = progress.getLastStepId() == null
                ? null
                : courseStepJpaRepository.findByIdAndCourseId(progress.getLastStepId(), courseId).orElse(null);
        if (currentStep == null || currentStep.getStepOrder() < lastRequiredStep.getStepOrder()) {
            throw new BaseException(ErrorCode.REQUIRED_STEP_NOT_COMPLETED);
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
