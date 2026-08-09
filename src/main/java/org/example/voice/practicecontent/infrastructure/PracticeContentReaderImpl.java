package org.example.voice.practicecontent.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.controller.dto.PracticeContentNextConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.domain.model.PracticeContentDetailData;
import org.example.voice.practicecontent.domain.model.PracticeContentPageData;
import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;
import org.example.voice.practicecontent.domain.port.PracticeContentReader;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PracticeContentReaderImpl implements PracticeContentReader {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Override
    public PracticeContentPageData<PracticeContentSummaryData> findPracticeContents(PracticeContentQueryConditionDto condition) {
        QueryParts queryParts = buildPracticeContentQuery(condition);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            pc.id,
                            pc.content_type,
                            pc.title,
                            pc.category,
                            pc.difficulty,
                            pc.estimated_seconds,
                            pc.script_text
                        from practice_contents pc
                        %s
                        order by pc.published_at desc nulls last, pc.created_at desc
                        limit :limit offset :offset
                        """.formatted(queryParts.whereClause()))
                .setParameter("limit", condition.size())
                .setParameter("offset", condition.page() * condition.size())
                .unwrap(org.hibernate.query.NativeQuery.class)
                .setProperties(queryParts.parameters())
                .getResultList();

        Number totalElements = (Number) entityManager.createNativeQuery("""
                        select count(*)
                        from practice_contents pc
                        %s
                        """.formatted(queryParts.whereClause()))
                .unwrap(org.hibernate.query.NativeQuery.class)
                .setProperties(queryParts.parameters())
                .getSingleResult();

        List<PracticeContentSummaryData> items = rows.stream()
                .map(this::toSummaryData)
                .toList();
        return PracticeContentPageData.of(items, condition.page(), condition.size(), totalElements.longValue());
    }

    @Override
    public Optional<PracticeContentDetailData> findPracticeContent(Long contentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            pc.id,
                            pc.content_type,
                            pc.learning_focus,
                            pc.category,
                            pc.title,
                            pc.description,
                            pc.script_text,
                            pc.difficulty,
                            pc.target_pronunciations,
                            pc.estimated_seconds,
                            exists (
                                select 1
                                from reference_audios ra
                                where ra.content_id = pc.id
                            ) as reference_audio_available
                        from practice_contents pc
                        where pc.id = :contentId
                          and pc.status = 'PUBLISHED'
                        """)
                .setParameter("contentId", contentId)
                .getResultList();

        return rows.stream()
                .findFirst()
                .map(row -> new PracticeContentDetailData(
                        toLong(row[0]),
                        ContentType.valueOf((String) row[1]),
                        LearningFocus.valueOf((String) row[2]),
                        (String) row[3],
                        (String) row[4],
                        (String) row[5],
                        (String) row[6],
                        Difficulty.valueOf((String) row[7]),
                        parseStringList(row[8]),
                        toInteger(row[9]),
                        (Boolean) row[10]
                ));
    }

    @Override
    public Optional<PracticeContentSummaryData> findNextPracticeContent(PracticeContentNextConditionDto condition) {
        QueryParts queryParts = buildNextContentQuery(condition);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            pc.id,
                            pc.content_type,
                            pc.title,
                            pc.category,
                            pc.difficulty,
                            pc.estimated_seconds,
                            pc.script_text
                        from practice_contents pc
                        %s
                        order by pc.id asc
                        limit 1
                        """.formatted(queryParts.whereClause()))
                .unwrap(org.hibernate.query.NativeQuery.class)
                .setProperties(queryParts.parameters())
                .getResultList();

        return rows.stream()
                .findFirst()
                .map(this::toSummaryData);
    }

    private QueryParts buildPracticeContentQuery(PracticeContentQueryConditionDto condition) {
        List<String> conditions = new ArrayList<>();
        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        conditions.add("pc.status = 'PUBLISHED'");
        if (condition.type() != null) {
            conditions.add("pc.content_type = :type");
            parameters.put("type", condition.type().name());
        }
        if (condition.category() != null && !condition.category().isBlank()) {
            conditions.add("pc.category = :category");
            parameters.put("category", condition.category());
        }
        if (condition.difficulty() != null) {
            conditions.add("pc.difficulty = :difficulty");
            parameters.put("difficulty", condition.difficulty().name());
        }
        if (condition.focus() != null) {
            conditions.add("pc.learning_focus = :focus");
            parameters.put("focus", condition.focus().name());
        }
        return new QueryParts("where " + String.join(" and ", conditions), parameters);
    }

    private QueryParts buildNextContentQuery(PracticeContentNextConditionDto condition) {
        List<String> conditions = new ArrayList<>();
        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        conditions.add("pc.status = 'PUBLISHED'");
        conditions.add("pc.content_type = :type");
        parameters.put("type", condition.type().name());
        if (condition.category() != null && !condition.category().isBlank()) {
            conditions.add("pc.category = :category");
            parameters.put("category", condition.category());
        }
        if (condition.difficulty() != null) {
            conditions.add("pc.difficulty = :difficulty");
            parameters.put("difficulty", condition.difficulty().name());
        }
        if (condition.excludeId() != null) {
            conditions.add("pc.id <> :excludeId");
            parameters.put("excludeId", condition.excludeId());
        }
        return new QueryParts("where " + String.join(" and ", conditions), parameters);
    }

    private PracticeContentSummaryData toSummaryData(Object[] row) {
        return new PracticeContentSummaryData(
                toLong(row[0]),
                ContentType.valueOf((String) row[1]),
                (String) row[2],
                (String) row[3],
                Difficulty.valueOf((String) row[4]),
                toInteger(row[5]),
                (String) row[6]
        );
    }

    private List<String> parseStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private record QueryParts(
            String whereClause,
            java.util.Map<String, Object> parameters
    ) {
    }
}
