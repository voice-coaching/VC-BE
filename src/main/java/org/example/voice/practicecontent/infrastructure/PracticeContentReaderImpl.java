package org.example.voice.practicecontent.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.controller.dto.PracticeContentNextConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.model.PracticeContentDetailData;
import org.example.voice.practicecontent.domain.model.PracticeContentPageData;
import org.example.voice.practicecontent.domain.model.PracticeContentRecommendationData;
import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;
import org.example.voice.practicecontent.domain.port.PracticeContentReader;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeContentReaderImpl implements PracticeContentReader {

    private static final int RECOMMENDATION_LIMIT = 5;
    private static final String CONTENT_RECOMMENDATION_REASON = "같은 난이도와 학습 초점의 콘텐츠입니다.";

    private final PracticeContentJpaRepository practiceContentJpaRepository;
    private final ReferenceAudioJpaRepository referenceAudioJpaRepository;

    @Override
    @Cacheable(
            cacheNames = PracticeContentCacheNames.LIST,
            key = "T(org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheKeys).list(#p0)"
    )
    public PracticeContentPageData<PracticeContentSummaryData> findPracticeContents(PracticeContentQueryConditionDto condition) {
        PageRequest pageRequest = PageRequest.of(
                condition.page(),
                condition.size(),
                Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("createdAt"))
        );
        Page<PracticeContent> page = practiceContentJpaRepository.findAll(searchSpec(condition), pageRequest);
        List<PracticeContentSummaryData> items = page.getContent().stream()
                .map(this::toSummaryData)
                .toList();
        return PracticeContentPageData.of(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    @Cacheable(
            cacheNames = PracticeContentCacheNames.DETAIL,
            key = "#p0",
            unless = "#result == null"
    )
    public Optional<PracticeContentDetailData> findPracticeContent(Long contentId) {
        return practiceContentJpaRepository.findById(contentId)
                .filter(PracticeContent::isPublished)
                .map(content -> new PracticeContentDetailData(
                        content.getId(),
                        content.getContentType(),
                        content.getLearningFocus(),
                        content.getCategory(),
                        content.getTitle(),
                        content.getDescription(),
                        content.getScriptText(),
                        content.getDifficulty(),
                        content.getTargetPronunciations() == null ? List.of() : content.getTargetPronunciations(),
                        content.getEstimatedSeconds(),
                        referenceAudioJpaRepository.existsByContentId(content.getId())
                ));
    }

    @Override
    @Cacheable(
            cacheNames = PracticeContentCacheNames.NEXT,
            key = "T(org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheKeys).next(#p0)",
            unless = "#result == null"
    )
    public Optional<PracticeContentSummaryData> findNextPracticeContent(PracticeContentNextConditionDto condition) {
        Page<PracticeContent> page = practiceContentJpaRepository.findAll(
                nextSpec(condition),
                PageRequest.of(0, 1, Sort.by("id").ascending())
        );
        return page.getContent().stream()
                .findFirst()
                .map(this::toSummaryData);
    }

    @Override
    @Cacheable(
            cacheNames = PracticeContentCacheNames.RECOMMENDATIONS,
            key = "#p0",
            unless = "#result == null"
    )
    public Optional<List<PracticeContentRecommendationData>> findRecommendationsByContentId(Long contentId) {
        return practiceContentJpaRepository.findById(contentId)
                .filter(PracticeContent::isPublished)
                .map(content -> practiceContentJpaRepository.findAll(
                                recommendationSpec(content),
                                PageRequest.of(
                                        0,
                                        RECOMMENDATION_LIMIT,
                                        Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("createdAt"))
                                )
                        )
                        .getContent()
                        .stream()
                        .map(this::toRecommendationData)
                        .toList()
                );
    }

    private Specification<PracticeContent> searchSpec(PracticeContentQueryConditionDto condition) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("status"), PublishStatus.PUBLISHED);
            if (condition.type() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("contentType"), condition.type()));
            }
            if (condition.category() != null && !condition.category().isBlank()) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("category"), condition.category()));
            }
            if (condition.difficulty() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("difficulty"), condition.difficulty()));
            }
            if (condition.focus() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("learningFocus"), condition.focus()));
            }
            return predicate;
        };
    }

    private Specification<PracticeContent> nextSpec(PracticeContentNextConditionDto condition) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("status"), PublishStatus.PUBLISHED),
                    criteriaBuilder.equal(root.get("contentType"), condition.type())
            );
            if (condition.category() != null && !condition.category().isBlank()) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("category"), condition.category()));
            }
            if (condition.difficulty() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("difficulty"), condition.difficulty()));
            }
            if (condition.excludeId() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.notEqual(root.get("id"), condition.excludeId()));
            }
            return predicate;
        };
    }

    private Specification<PracticeContent> recommendationSpec(PracticeContent content) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("status"), PublishStatus.PUBLISHED),
                criteriaBuilder.notEqual(root.get("id"), content.getId()),
                criteriaBuilder.equal(root.get("difficulty"), content.getDifficulty()),
                criteriaBuilder.equal(root.get("learningFocus"), content.getLearningFocus())
        );
    }

    private PracticeContentSummaryData toSummaryData(PracticeContent content) {
        return new PracticeContentSummaryData(
                content.getId(),
                content.getContentType(),
                content.getTitle(),
                content.getCategory(),
                content.getDifficulty(),
                content.getEstimatedSeconds(),
                content.getScriptText()
        );
    }

    private PracticeContentRecommendationData toRecommendationData(PracticeContent content) {
        return new PracticeContentRecommendationData(
                content.getId(),
                content.getTitle(),
                content.getContentType(),
                CONTENT_RECOMMENDATION_REASON
        );
    }
}
