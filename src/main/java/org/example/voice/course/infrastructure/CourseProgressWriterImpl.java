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
import org.example.voice.course.infrastructure.cache.CourseCacheNames;
import org.example.voice.home.infrastructure.cache.HomeCacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
    @Caching(evict = {
            @CacheEvict(cacheNames = HomeCacheNames.COURSE_PROGRESS, allEntries = true),
            @CacheEvict(cacheNames = CourseCacheNames.LIST, allEntries = true),
            @CacheEvict(
                    cacheNames = CourseCacheNames.DETAIL,
                    key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)"
            ),
            @CacheEvict(
                    cacheNames = CourseCacheNames.STEPS,
                    key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)"
            )
    })
    public CourseProgressData startCourse(Long courseId, Long userId) {
        Course course = courseJpaRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        return userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .map(progress -> validateAndReturnStartedProgress(progress, courseId))
                .orElseGet(() -> createProgress(course, userId));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = HomeCacheNames.COURSE_PROGRESS, allEntries = true),
            @CacheEvict(cacheNames = CourseCacheNames.LIST, allEntries = true),
            @CacheEvict(
                    cacheNames = CourseCacheNames.DETAIL,
                    key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)"
            ),
            @CacheEvict(
                    cacheNames = CourseCacheNames.STEPS,
                    key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)"
            )
    })
    public CourseProgressData updateCourseProgress(Long courseId, Long userId, CourseProgressUpdateRequestDto request) {

        UserCourseProgress progress = userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        progress.updateProgress(request.lastStepId(), BigDecimal.valueOf(request.progressPercent()), OffsetDateTime.now());
        return toData(progress);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = HomeCacheNames.COURSE_PROGRESS, allEntries = true),
            @CacheEvict(cacheNames = CourseCacheNames.LIST, allEntries = true),
            @CacheEvict(
                    cacheNames = CourseCacheNames.DETAIL,
                    key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)"
            ),
            @CacheEvict(
                    cacheNames = CourseCacheNames.STEPS,
                    key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)"
            )
    })
    public CourseProgressData completeCourse(Long courseId, Long userId) {
        UserCourseProgress progress = userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        progress.complete(OffsetDateTime.now());
        return toData(progress);
    }

    private CourseProgressData validateAndReturnStartedProgress(UserCourseProgress progress, Long courseId) {

        return toData(progress);
    }

    private CourseProgressData createProgress(Course course, Long userId) {
        CourseStep firstStep = courseStepJpaRepository.findFirstByCourseIdAndRetiredFalseOrderByStepOrderAscIdAsc(course.getId())
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
