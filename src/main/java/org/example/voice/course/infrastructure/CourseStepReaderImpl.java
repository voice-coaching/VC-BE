package org.example.voice.course.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.entity.CourseStep;
import org.example.voice.course.domain.entity.UserCourseProgress;
import org.example.voice.course.domain.model.CourseStepData;
import org.example.voice.course.domain.model.CourseStepListData;
import org.example.voice.course.domain.port.CourseStepReader;
import org.example.voice.course.infrastructure.cache.CourseCacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseStepReaderImpl implements CourseStepReader {

    private final CourseJpaRepository courseJpaRepository;
    private final CourseStepJpaRepository courseStepJpaRepository;
    private final UserCourseProgressJpaRepository userCourseProgressJpaRepository;

    @Override
    @Cacheable(
            cacheNames = CourseCacheNames.STEPS,
            key = "T(org.example.voice.course.infrastructure.cache.CourseCacheKeys).userCourse(#p1, #p0)",
            unless = "#result == null"
    )
    public Optional<CourseStepListData> findCourseSteps(Long courseId, Long userId) {
        if (!courseJpaRepository.existsById(courseId)) {
            return Optional.empty();
        }

        List<CourseStep> steps = courseStepJpaRepository.findByCourseIdOrderByStepOrderAscIdAsc(courseId);
        Integer completedStepOrder = findCompletedStepOrder(userId, courseId, steps);
        List<CourseStepData> items = steps.stream()
                .map(step -> toData(step, completedStepOrder))
                .toList();

        return Optional.of(new CourseStepListData(items));
    }

    private Integer findCompletedStepOrder(Long userId, Long courseId, List<CourseStep> steps) {
        return userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .map(UserCourseProgress::getLastStepId)
                .flatMap(lastStepId -> findStepOrder(steps, lastStepId))
                .orElse(0);
    }

    private Optional<Integer> findStepOrder(List<CourseStep> steps, Long stepId) {
        return steps.stream()
                .filter(step -> step.getId().equals(stepId))
                .map(CourseStep::getStepOrder)
                .findFirst();
    }

    private CourseStepData toData(CourseStep step, Integer completedStepOrder) {
        Long practiceContentId = step.getPracticeContent() == null ? null : step.getPracticeContent().getId();
        return new CourseStepData(
                step.getId(),
                step.getStepOrder(),
                step.getStepType(),
                step.getTitle(),
                practiceContentId,
                step.getStepOrder() <= completedStepOrder
        );
    }
}
