package org.example.voice.practiceexample.infrastructure;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.entity.*;
import org.example.voice.practiceexample.domain.model.ExampleData.*;
import org.example.voice.practiceexample.domain.port.ExampleAudioCache;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ExampleAudioPersistence implements ExampleAudioCache {
    private final EntityManager em;
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Audio> cached(String key) {
        var row = em.find(ExampleAudioEntry.class, key);
        return row == null || row.getAudio() == null ? Optional.empty() : Optional.of(new Audio(row.getAudio(), row.getEtag()));
    }
    public Claim claim(String key, Long userId, OffsetDateTime now) {
        // Only cache misses serialize briefly; the network call runs after this transaction commits.
        var global = em.find(ExampleTtsQuota.class, 0L, LockModeType.PESSIMISTIC_WRITE);
        if (global == null) throw PracticeExampleException.unavailable();
        var row = em.find(ExampleAudioEntry.class, key, LockModeType.PESSIMISTIC_WRITE);
        if (row != null && row.getAudio() != null) return new Claim(new Audio(row.getAudio(), row.getEtag()), null);
        if (row != null && row.getLeaseUntil() != null && row.getLeaseUntil().isAfter(now)) throw PracticeExampleException.unavailable();
        long minute = now.toEpochSecond() / 60;
        global.consume(minute, 100);
        var user = em.find(ExampleTtsQuota.class, userId);
        if (user == null) { user = new ExampleTtsQuota(userId); em.persist(user); }
        user.consume(minute, 20);
        em.createQuery("delete from ExampleTtsQuota q where q.id<>0 and q.windowMinute<:expired").setParameter("expired", minute - 60).executeUpdate();
        if (row == null) { row = new ExampleAudioEntry(key); em.persist(row); }
        String lease = UUID.randomUUID().toString(); row.claim(lease, now.plusMinutes(5));
        return new Claim(null, lease);
    }
    public void complete(String key, String lease, Audio audio) {
        int changed = em.createQuery("update ExampleAudioEntry c set c.audio=:audio,c.etag=:etag,c.lease=null,c.leaseUntil=null where c.id=:id and c.lease=:lease and c.audio is null")
                .setParameter("audio", audio.bytes()).setParameter("etag", audio.etag()).setParameter("id", key).setParameter("lease", lease).executeUpdate();
        if (changed != 1) throw PracticeExampleException.unavailable();
    }
    public void fail(String key, String lease, OffsetDateTime now) {
        em.createQuery("update ExampleAudioEntry c set c.lease=null,c.leaseUntil=:until where c.id=:id and c.lease=:lease and c.audio is null")
                .setParameter("until", now.plusSeconds(5)).setParameter("id", key).setParameter("lease", lease).executeUpdate();
    }
}
