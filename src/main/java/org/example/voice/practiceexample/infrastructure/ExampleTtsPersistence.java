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
        db.update("INSERT INTO example_tts_profiles(revision,fingerprint) VALUES (?,?) ON CONFLICT DO NOTHING", revision, fingerprint);
        if (!fingerprint.equals(db.queryForObject("SELECT fingerprint FROM example_tts_profiles WHERE revision=?", String.class, revision)))
            throw new IllegalStateException("TTS_PROFILE_CONFLICT");
        db.update("""
            INSERT INTO example_tts_jobs(example_id,profile_revision)
            SELECT e.id,? FROM practice_examples e JOIN practice_contents c ON c.id=e.content_id
            WHERE c.owner_id IS NULL AND c.status='PUBLISHED'
            AND NOT EXISTS(SELECT 1 FROM example_tts_jobs j WHERE j.example_id=e.id AND j.profile_revision=?)
            ORDER BY e.id LIMIT 100 ON CONFLICT DO NOTHING
            """, revision, revision);
        db.update("""
            UPDATE example_tts_outbox o SET processed_at=now() WHERE processed_at IS NULL
            AND EXISTS(SELECT 1 FROM example_tts_jobs j WHERE j.example_id=o.example_id AND j.profile_revision=?)
            """, revision);
        db.update("""
            UPDATE example_tts_jobs SET state='FAILED',lease_owner=NULL,lease_until=NULL,last_error_code='LEASE_EXHAUSTED',updated_at=now()
            WHERE state='RUNNING' AND lease_until<now() AND attempt>=5
            """);
    }
    @Override @Transactional
    public Optional<Job> claim(String revision) {
        var ids=db.queryForList("""
            SELECT id FROM example_tts_jobs WHERE profile_revision=? AND attempt<5
            AND ((state IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=now()) OR (state='RUNNING' AND lease_until<now()))
            ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1
            """, Long.class, revision);
        if(ids.isEmpty()) return Optional.empty();
        UUID owner=UUID.randomUUID(); long id=ids.getFirst();
        db.update("UPDATE example_tts_jobs SET state='RUNNING',attempt=attempt+1,lease_owner=?,lease_until=now()+interval '90 seconds',updated_at=now() WHERE id=?",owner,id);
        return db.query("""
            SELECT j.id,j.example_id,c.script_text,s.revision,j.profile_revision,j.attempt
            FROM example_tts_jobs j JOIN practice_examples e ON e.id=j.example_id
            JOIN practice_example_sets s ON s.id=e.set_id JOIN practice_contents c ON c.id=e.content_id WHERE j.id=?
            """, (rs,n)->new Job(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5),owner,rs.getInt(6)),id).stream().findFirst();
    }
    @Override @Transactional
    public boolean complete(Job job, Generated audio) {
        var owned=db.queryForList("SELECT id FROM example_tts_jobs WHERE id=? AND state='RUNNING' AND lease_owner=? AND lease_until>now() FOR UPDATE",Long.class,job.id(),job.owner());
        if(owned.isEmpty()) return false;
        // The job identity is unique for the immutable example/profile pair.
        String key=hash(("runpod-job:"+job.id()+":"+audio.sha256()).getBytes(StandardCharsets.UTF_8));
        db.update("INSERT INTO example_audio_cache(id,audio,etag) VALUES (?,?,?) ON CONFLICT DO NOTHING",key,audio.bytes(),'"'+audio.sha256()+'"');
        db.update("UPDATE example_tts_jobs SET state='GENERATED',cache_key=?,audio_sha256=?,duration_ms=?,lease_owner=NULL,lease_until=NULL,last_error_code=NULL,updated_at=now() WHERE id=?",key,audio.sha256(),audio.durationMs(),job.id());
        return true;
    }
    @Override @Transactional
    public void fail(Job job,String code,boolean retryable,long delaySeconds) {
        db.update("""
            UPDATE example_tts_jobs SET state=?,last_error_code=?,lease_owner=NULL,lease_until=NULL,
            next_attempt_at=now()+(? * interval '1 second'),updated_at=now() WHERE id=? AND state='RUNNING' AND lease_owner=?
            """,retryable && job.attempt()<5 ? "RETRY_WAIT":"FAILED",code,delaySeconds,job.id(),job.owner());
    }
    @Override @Transactional(readOnly=true)
    public Optional<Audio> approved(String exampleId,String revision) {
        return db.query("""
            SELECT c.audio,c.etag,j.audio_sha256 FROM example_tts_jobs j JOIN example_audio_cache c ON c.id=j.cache_key
            WHERE j.example_id=? AND j.profile_revision=? AND j.state='GENERATED'
            AND (SELECT count(*) FROM example_audio_approvals a WHERE a.job_id=j.id AND a.audio_sha256=j.audio_sha256 AND a.decision='APPROVED')>=2
            AND NOT EXISTS(SELECT 1 FROM example_audio_approvals a WHERE a.job_id=j.id AND a.audio_sha256=j.audio_sha256 AND a.decision='REJECTED')
            """,(rs,n)-> {
                byte[] bytes=rs.getBytes(1);String digest=hash(bytes);
                if(!digest.equals(rs.getString(3)) || !('"'+digest+'"').equals(rs.getString(2))) throw new IllegalStateException("TTS_CACHE_DIGEST_MISMATCH");
                return new Audio(bytes,rs.getString(2));
            },exampleId,revision).stream().findFirst();
    }
    public static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
