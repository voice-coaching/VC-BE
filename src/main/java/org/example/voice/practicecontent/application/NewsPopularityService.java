package org.example.voice.practicecontent.application;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.practicecontent.controller.dto.PopularNewsResponseDto;
import org.example.voice.practicecontent.domain.model.ContentTextMetadata;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsPopularityService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** A unique day row claims the build across instances; the complete snapshot commits atomically. */
    @Transactional
    public PopularNewsResponseDto get(ContentType type, int limit) {

        var now = clock.instant();
        var day = now.atZone(SEOUL).toLocalDate();
        var end = day.atStartOfDay(SEOUL).toInstant();
        var start = day.minusDays(1).atStartOfDay(SEOUL).toInstant();
        var next = day.plusDays(1).atStartOfDay(SEOUL).toInstant();
        var date = Date.valueOf(day);
        int claimed = jdbc.update("""
                INSERT INTO news_popularity_snapshots
                    (ranking_date, period_start, period_end, generated_at, next_update_at, ranking_basis)
                VALUES (?, ?, ?, ?, ?, 'CATALOG_ORDER') ON CONFLICT (ranking_date) DO UPDATE SET period_start=EXCLUDED.period_start,period_end=EXCLUDED.period_end,generated_at=EXCLUDED.generated_at,next_update_at=EXCLUDED.next_update_at,ranking_basis=EXCLUDED.ranking_basis
""", date, Timestamp.from(start), Timestamp.from(end), Timestamp.from(now), Timestamp.from(next));
        if (claimed == 1) {
            jdbc.update("""
                    WITH usage AS (
                        SELECT t.content_id, COUNT(DISTINCT t.user_id) AS usage_count
                        FROM training_sessions t JOIN users u ON u.id=t.user_id
                        JOIN practice_contents c ON c.id=t.content_id
                        WHERE t.status='COMPLETED' AND t.completed_at>=? AND t.completed_at<?
                          AND u.status='ACTIVE' AND u.deleted_at IS NULL
                          AND c.content_type='NEWS' AND c.status='PUBLISHED' AND c.owner_id IS NULL
                          AND (c.published_at IS NULL OR c.published_at<=?)
                        GROUP BY t.content_id
                    )
                    INSERT INTO news_popularity_items (ranking_date, content_id, position, usage_count)
                    SELECT ?, c.id, ROW_NUMBER() OVER (
                        ORDER BY COALESCE(u.usage_count,0) DESC,
                            c.published_at DESC NULLS LAST, c.created_at DESC, c.id DESC
                    ), COALESCE(u.usage_count,0)
                    FROM practice_contents c LEFT JOIN usage u ON u.content_id=c.id
                    WHERE c.content_type='NEWS' AND c.status='PUBLISHED' AND c.owner_id IS NULL
                      AND (c.published_at IS NULL OR c.published_at<=?)
                    """, Timestamp.from(start), Timestamp.from(end), Timestamp.from(now), date, Timestamp.from(now));
            jdbc.update("""
                    UPDATE news_popularity_snapshots SET ranking_basis='COMPLETED_USERS'
                    WHERE ranking_date=? AND EXISTS (
                        SELECT 1 FROM news_popularity_items WHERE ranking_date=? AND usage_count>0
                    )
                    """, date, date);
        }
        var header = jdbc.queryForMap("SELECT * FROM news_popularity_snapshots WHERE ranking_date=?", date);
        List<PopularNewsResponseDto.Item> items = new ArrayList<>();
        // Publication changes take effect immediately. New eligible content fills unused slots
        // after the persisted ranks; it receives usage counts at the next daily build.
        jdbc.query("""
                SELECT c.*, COALESCE(i.usage_count,0) AS usage_count
                FROM practice_contents c LEFT JOIN news_popularity_items i
                  ON i.content_id=c.id AND i.ranking_date=?
                WHERE c.content_type='NEWS' AND c.status='PUBLISHED' AND c.owner_id IS NULL
                  AND (c.published_at IS NULL OR c.published_at<=?)
                ORDER BY CASE WHEN i.position IS NULL THEN 1 ELSE 0 END, i.position,
                    c.published_at DESC NULLS LAST, c.created_at DESC, c.id DESC LIMIT ?
                """, rs -> {
                    var metadata = ContentTextMetadata.from(rs.getString("script_text"));
                    items.add(new PopularNewsResponseDto.Item(items.size()+1, rs.getLong("usage_count"),
                            rs.getLong("id"), ContentType.NEWS, rs.getString("title"), rs.getString("category"),
                            Difficulty.valueOf(rs.getString("difficulty")), rs.getInt("estimated_seconds"),
                            rs.getString("publisher"), metadata.paragraphCount(), metadata.sentenceCount(),
                            rs.getObject("published_at", OffsetDateTime.class)));
                }, date, Timestamp.from(now), limit);
        return new PopularNewsResponseDto(ContentType.NEWS, (String) header.get("ranking_basis"),
                timestamp(header.get("period_start")), timestamp(header.get("period_end")),
                timestamp(header.get("generated_at")), timestamp(header.get("next_update_at")), List.copyOf(items));
    }

    private static OffsetDateTime timestamp(Object value) {
        return value instanceof OffsetDateTime date ? date.withOffsetSameInstant(ZoneOffset.UTC)
                : ((Timestamp) value).toInstant().atOffset(ZoneOffset.UTC);
    }
}
