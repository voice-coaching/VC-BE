package org.example.voice.course.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.controller.dto.CourseSearchConditionDto;
import org.example.voice.course.domain.entity.Course;
import org.example.voice.course.domain.entity.UserCourseProgress;
import org.example.voice.course.domain.model.CourseDetailData;
import org.example.voice.course.domain.model.CoursePageData;
import org.example.voice.course.domain.model.CourseProgressSummaryData;
import org.example.voice.course.domain.model.CourseSummaryData;
import org.example.voice.course.domain.port.CourseReader;
import org.example.voice.course.domain.type.CourseProgressStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseReaderImpl implements CourseReader {

    private final CourseJpaRepository courseJpaRepository;
    private final CourseStepJpaRepository courseStepJpaRepository;
    private final UserCourseProgressJpaRepository userCourseProgressJpaRepository;

    @Override
    public CoursePageData<CourseSummaryData> findCourses(CourseSearchConditionDto condition, Long userId) {
        PageRequest pageRequest = PageRequest.of(
                condition.page(),
                condition.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<Course> page = courseJpaRepository.findAll(searchSpec(condition), pageRequest);
        Map<Long, BigDecimal> progressByCourseId = findProgressByCourseId(userId, page.getContent());
        List<CourseSummaryData> items = page.getContent().stream()
                .map(course -> toSummaryData(course, progressByCourseId.get(course.getId())))
                .toList();

        return CoursePageData.of(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    public Optional<CourseDetailData> findCourse(Long courseId, Long userId) {
        return courseJpaRepository.findById(courseId)
                .map(course -> toDetailData(course, findProgress(userId, courseId)));
    }

    private Specification<Course> searchSpec(CourseSearchConditionDto condition) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("status"), condition.status());
            if (condition.type() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("courseType"), condition.type()));
            }
            if (condition.difficulty() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("difficulty"), condition.difficulty()));
            }
            return predicate;
        };
    }

    private Map<Long, BigDecimal> findProgressByCourseId(Long userId, List<Course> courses) {
        if (courses.isEmpty()) {
            return Map.of();
        }
        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .toList();
        return userCourseProgressJpaRepository.findByUserIdAndCourseIdIn(userId, courseIds)
                .stream()
                .collect(Collectors.toMap(progress -> progress.getCourse().getId(), UserCourseProgress::getProgressPercent));
    }

    private CourseSummaryData toSummaryData(Course course, BigDecimal progressPercent) {
        return new CourseSummaryData(
                course.getId(),
                course.getCourseType(),
                course.getTitle(),
                course.getDifficulty(),
                course.getEstimatedMinutes(),
                progressPercent == null ? 0.0 : progressPercent.doubleValue()
        );
    }

    private CourseDetailData toDetailData(Course course, CourseProgressSummaryData progress) {
        return new CourseDetailData(
                course.getId(),
                course.getCourseType(),
                course.getTitle(),
                course.getDescription(),
                course.getDifficulty(),
                course.getEstimatedMinutes(),
                courseStepJpaRepository.countByCourseId(course.getId()),
                progress
        );
    }

    private CourseProgressSummaryData findProgress(Long userId, Long courseId) {
        return userCourseProgressJpaRepository.findByUserIdAndCourseId(userId, courseId)
                .map(progress -> new CourseProgressSummaryData(
                        progress.getStatus(),
                        progress.getProgressPercent().doubleValue(),
                        validLastStepId(progress.getLastStepId(), courseId)
                ))
                .orElseGet(() -> new CourseProgressSummaryData(CourseProgressStatus.NOT_STARTED, 0.0, null));
    }

    private Long validLastStepId(Long lastStepId, Long courseId) {
        if (lastStepId == null) {
            return null;
        }
        return courseStepJpaRepository.existsByIdAndCourseId(lastStepId, courseId) ? lastStepId : null;
    }
}
