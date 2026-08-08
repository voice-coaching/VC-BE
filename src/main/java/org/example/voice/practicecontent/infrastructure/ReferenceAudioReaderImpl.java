package org.example.voice.practicecontent.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.model.ReferenceAudioData;
import org.example.voice.practicecontent.domain.model.ReferenceAudioPlaybackUrlData;
import org.example.voice.practicecontent.domain.port.ReferenceAudioReader;
import org.example.voice.practicecontent.domain.type.SpeakerType;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReferenceAudioReaderImpl implements ReferenceAudioReader {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final EntityManager entityManager;

    @Override
    public boolean existsPracticeContent(Long contentId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        select count(*)
                        from practice_contents pc
                        where pc.id = :contentId
                          and pc.status = 'PUBLISHED'
                        """)
                .setParameter("contentId", contentId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public List<ReferenceAudioData> findReferenceAudiosByContentId(Long contentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            ra.id,
                            ra.speaker_name,
                            ra.speaker_type,
                            ra.duration_ms,
                            ra.is_primary
                        from reference_audios ra
                        where ra.content_id = :contentId
                        order by ra.is_primary desc, ra.id asc
                        """)
                .setParameter("contentId", contentId)
                .getResultList();

        return rows.stream()
                .map(row -> new ReferenceAudioData(
                        toLong(row[0]),
                        (String) row[1],
                        row[2] == null ? null : SpeakerType.valueOf((String) row[2]),
                        toInteger(row[3]),
                        (Boolean) row[4]
                ))
                .toList();
    }

    @Override
    public Optional<ReferenceAudioPlaybackUrlData> findPlaybackUrl(Long audioId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            ra.id,
                            ra.audio_url
                        from reference_audios ra
                        where ra.id = :audioId
                        """)
                .setParameter("audioId", audioId)
                .getResultList();

        return rows.stream()
                .findFirst()
                .map(row -> new ReferenceAudioPlaybackUrlData(
                        toLong(row[0]),
                        (String) row[1],
                        OffsetDateTime.now(SEOUL_ZONE_ID).plusMinutes(10)
                ));
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
