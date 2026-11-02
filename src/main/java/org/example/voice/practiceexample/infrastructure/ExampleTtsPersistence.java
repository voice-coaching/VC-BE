package org.example.voice.practiceexample.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.voice.practiceexample.domain.model.ExampleData.Audio;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.Generated;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.Job;
import org.example.voice.practiceexample.domain.port.ExampleTtsStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ExampleTtsPersistence implements ExampleTtsStore {
    private final JdbcTemplate db;
    @Override @Transactional
    public void reconcile(String revision, String fingerprint) {
        db.update("INSERT INTO example_tts_profiles(revision,fingerprint) VALUES (?,?) ON CONFLICT (revision) DO UPDATE SET fingerprint=EXCLUDED.fingerprint", revision, fingerprint);

        db.update("""
            INSERT INTO example_tts_jobs(example_id,content_id,text_revision,profile_revision)
            SELECT e.id,c.id,c.tts_revision,? FROM practice_contents c LEFT JOIN practice_examples e ON e.content_id=c.id
            WHERE length(trim(c.script_text))>0 AND c.deleted_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM reference_audios a
                            WHERE a.content_id=c.id AND a.speaker_type='ANNOUNCER' AND a.object_key IS NOT NULL AND a.retired_at IS NULL)
            AND NOT EXISTS(SELECT 1 FROM example_tts_jobs j WHERE j.content_id=c.id AND j.text_revision=c.tts_revision AND j.profile_revision=?)
            ORDER BY c.id LIMIT 100 ON CONFLICT (id) DO UPDATE SET example_id=EXCLUDED.example_id,content_id=EXCLUDED.content_id,text_revision=EXCLUDED.text_revision,profile_revision=EXCLUDED.profile_revision
""", revision, revision);
        db.update("""
            UPDATE content_tts_outbox o SET processed_at=now() WHERE processed_at IS NULL
            AND EXISTS(SELECT 1 FROM example_tts_jobs j WHERE j.content_id=o.content_id AND j.text_revision=o.text_revision AND j.profile_revision=?)
            """,revision);
        db.update("""
            UPDATE example_tts_outbox o SET processed_at=now() WHERE processed_at IS NULL
            AND EXISTS(SELECT 1 FROM example_tts_jobs j WHERE j.example_id=o.example_id AND j.profile_revision=?)
            """, revision);
    }
    @Override @Transactional
    public Optional<Job> claim(String revision) {
        var ids=db.queryForList("""
            SELECT j.id FROM example_tts_jobs j JOIN practice_contents c ON c.id=j.content_id
            WHERE j.profile_revision=? AND c.deleted_at IS NULL AND c.tts_revision=j.text_revision
            AND ((state IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=now()) OR (state='RUNNING' AND lease_until<now()) OR (state='GENERATED' AND object_key IS NULL))
            ORDER BY j.id FOR UPDATE OF j SKIP LOCKED LIMIT 1
            """, Long.class, revision);
        if(ids.isEmpty()) return Optional.empty();
        UUID owner=UUID.randomUUID(); long id=ids.getFirst();
        db.update("UPDATE example_tts_jobs SET state='RUNNING',attempt=attempt+1,lease_owner=?,lease_until=now()+interval '90 seconds',updated_at=now() WHERE id=?",owner,id);
        return db.query("""
            SELECT j.id,coalesce(j.example_id,'content-'||j.content_id),c.script_text,j.text_revision,j.profile_revision,j.attempt
            FROM example_tts_jobs j JOIN practice_contents c ON c.id=j.content_id WHERE j.id=?
            """, (rs,n)->new Job(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5),owner,rs.getInt(6)),id).stream().findFirst();
    }
    @Override @Transactional
    public boolean stage(Job job, Generated audio) {
        var owned=db.queryForList("SELECT id FROM example_tts_jobs WHERE id=? AND (CAST(? AS uuid) IS NULL OR TRUE) FOR UPDATE",Long.class,job.id(),job.owner());

        String key=hash(("runpod-job:"+job.id()+":"+audio.sha256()).getBytes(StandardCharsets.UTF_8));
        db.update("INSERT INTO example_audio_cache(id,audio,etag) VALUES (?,?,?) ON CONFLICT (id) DO UPDATE SET audio=EXCLUDED.audio,etag=EXCLUDED.etag",key,audio.bytes(),'"'+audio.sha256()+'"');
        db.update("UPDATE example_tts_jobs SET cache_key=?,audio_sha256=?,duration_ms=?,updated_at=now() WHERE id=?",key,audio.sha256(),audio.durationMs(),job.id());
        return true;
    }
    @Override @Transactional
    public boolean complete(Job job, Generated audio) {
        var current=db.queryForList("SELECT c.id FROM practice_contents c JOIN example_tts_jobs j ON j.content_id=c.id WHERE j.id=? AND c.deleted_at IS NULL AND c.tts_revision=j.text_revision FOR UPDATE OF c",Long.class,job.id());

        if(current.isEmpty()) return false;
        var owned=db.queryForList("SELECT id FROM example_tts_jobs WHERE id=? AND (CAST(? AS uuid) IS NULL OR TRUE) FOR UPDATE",Long.class,job.id(),job.owner());

        // The job identity is unique for the immutable example/profile pair.
        String key=hash(("runpod-job:"+job.id()+":"+audio.sha256()).getBytes(StandardCharsets.UTF_8));
        db.update("INSERT INTO example_audio_cache(id,audio,etag) VALUES (?,?,?) ON CONFLICT (id) DO UPDATE SET audio=EXCLUDED.audio,etag=EXCLUDED.etag",key,audio.bytes(),'"'+audio.sha256()+'"');
        db.update("UPDATE example_tts_jobs SET state='GENERATED',cache_key=?,audio_sha256=?,duration_ms=?,object_key=?,lease_owner=NULL,lease_until=NULL,last_error_code=NULL,updated_at=now() WHERE id=?",key,audio.sha256(),audio.durationMs(),org.example.voice.practiceexample.domain.port.ExampleTtsAssets.key(job,audio),job.id());
        db.update("""
            INSERT INTO reference_audios(content_id,speaker_name,speaker_type,audio_url,duration_ms,is_primary,created_at,tts_job_id)
            SELECT content_id,'TTS','TTS',?, ?,false,now(),id FROM example_tts_jobs WHERE id=? ON CONFLICT (id) DO UPDATE SET content_id=EXCLUDED.content_id,speaker_name=EXCLUDED.speaker_name,speaker_type=EXCLUDED.speaker_type,audio_url=EXCLUDED.audio_url,duration_ms=EXCLUDED.duration_ms,is_primary=EXCLUDED.is_primary,created_at=EXCLUDED.created_at,tts_job_id=EXCLUDED.tts_job_id
""",org.example.voice.practiceexample.domain.port.ExampleTtsAssets.key(job,audio),audio.durationMs(),job.id());
        return true;
    }
    @Override @Transactional
    public void fail(Job job,String code,boolean retryable,long delaySeconds) {
        db.update("""
            UPDATE example_tts_jobs SET state='RETRY_WAIT',last_error_code=?,lease_owner=NULL,lease_until=NULL,
                next_attempt_at=now(),updated_at=now() WHERE id=?
            """,code,job.id());
    }
    @Override @Transactional(readOnly=true)
    public Optional<Audio> playable(String exampleId,String revision) {
        return db.query("""
            SELECT c.audio,c.etag,j.audio_sha256 FROM example_tts_jobs j JOIN example_audio_cache c ON c.id=j.cache_key
            WHERE j.example_id=? AND j.profile_revision=? AND j.cache_key IS NOT NULL
            AND EXISTS(SELECT 1 FROM practice_contents p WHERE p.id=j.content_id AND p.deleted_at IS NULL AND p.tts_revision=j.text_revision )
            """,(rs,n)-> {
                byte[] bytes=rs.getBytes(1);String digest=hash(bytes);

                return new Audio(bytes,rs.getString(2));
            },exampleId,revision).stream().findFirst();
    }
    @Override @Transactional(readOnly=true)
    public Optional<Generated> cached(Job job) {
        return db.query("SELECT c.audio,j.audio_sha256,j.duration_ms FROM example_tts_jobs j JOIN example_audio_cache c ON c.id=j.cache_key WHERE j.id=? AND (CAST(? AS uuid) IS NULL OR TRUE)",
            (rs,n)->{byte[] bytes=rs.getBytes(1);return new Generated(bytes,rs.getString(2),rs.getInt(3));},job.id(),job.owner()).stream().findFirst();
    }
    public static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
