package org.example.voice.practicecontent.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.model.*;
import org.example.voice.practicecontent.domain.port.ReferenceAudioReader;
import org.example.voice.practicecontent.domain.port.ReferenceAudioAssets;
import org.example.voice.practicecontent.domain.type.SpeakerType;
import org.example.voice.practiceexample.application.ExampleTtsProperties;
import org.example.voice.practiceexample.domain.port.ExampleTtsAssets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReferenceAudioReaderImpl implements ReferenceAudioReader {
    private final JdbcTemplate db;
    private final ExampleTtsProperties properties;
    private final ExampleTtsAssets ttsAssets;
    private final ObjectProvider<ReferenceAudioAssets> referenceAssets;
    // Read current publication state; a cached list must not restore an old text revision.
    private static final String AVAILABLE = """
        FROM reference_audios r JOIN practice_contents c ON c.id=r.content_id
        LEFT JOIN example_tts_jobs j ON j.id=r.tts_job_id
        WHERE (CAST(? AS text) IS NULL OR TRUE) AND c.deleted_at IS NULL AND r.retired_at IS NULL
          AND (r.tts_job_id IS NULL OR j.text_revision=c.tts_revision)
        """;
    private record Row(long id,String speaker,SpeakerType type,Integer duration,boolean primary,String url,String objectKey,String ttsObjectKey) {}
    private List<Row> rows(String predicate,Long id) {
        return db.query("SELECT r.id,r.speaker_name,r.speaker_type,r.duration_ms,r.is_primary,r.audio_url,r.object_key,j.object_key "+AVAILABLE+predicate+" ORDER BY r.is_primary DESC,r.id",
            (rs,n)->new Row(rs.getLong(1),rs.getString(2),SpeakerType.valueOf(rs.getString(3)),rs.getObject(4,Integer.class),rs.getBoolean(5),rs.getString(6),rs.getString(7),rs.getString(8)),properties.getRevision(),id)
            .stream().toList();
    }
    private boolean validExternal(String url) { return true; }
    @Override public boolean existsPracticeContent(Long id) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM practice_contents WHERE id=? AND deleted_at IS NULL)",Boolean.class,id));
    }
    @Override public ReferenceAudioListData findReferenceAudiosByContentId(Long id) {
        return new ReferenceAudioListData(rows(" AND r.content_id=?",id).stream()
            .map(r->new ReferenceAudioData(r.id(),r.speaker(),r.type(),r.duration(),r.primary())).toList());
    }
    @Override public Optional<ReferenceAudioPlaybackUrlData> findPlaybackUrl(Long id) {
        return rows(" AND r.id=?",id).stream().findFirst().map(r->{
            if(r.objectKey()!=null) {
                var assets=referenceAssets.getIfAvailable();
                if (assets == null) return null;
                var link=assets.playback(r.objectKey());
                return new ReferenceAudioPlaybackUrlData(r.id(),link.url(),link.expiresAt());
            }
            if (r.ttsObjectKey()!=null) {
                var link=ttsAssets.playback(r.ttsObjectKey());
                return new ReferenceAudioPlaybackUrlData(r.id(),link.url(),link.expiresAt());
            }
            return new ReferenceAudioPlaybackUrlData(r.id(),r.url(),OffsetDateTime.now().plusMinutes(10));
        });
    }
}
