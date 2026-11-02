package org.example.voice.admin.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.ReferenceAudioUpload;
import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.admin.domain.port.AdminAudioStore;
import org.example.voice.common.exception.*;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.type.*;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class AdminAudioPersistence implements AdminAudioStore {
    private final EntityManager em;
    private static final String AUDIO="""
            select r.id,r.content.id,r.speakerName,r.speakerType,r.durationMs,r.primary,r.objectKey,r.createdAt
            from ReferenceAudio r where r.content.ownerId is null and r.content.contentType=:type
            and r.speakerType=:speaker and r.objectKey is not null
            """;
    @Override public void save(ReferenceAudioUpload upload) { em.persist(upload); }
    @Override public Optional<Upload> upload(UUID id,long actor,long content) {
        return query(id,actor,content,false).map(ReferenceAudioUpload::snapshot);
    }
    @Override public ReferenceAudioUpload lockUpload(UUID id,long actor,long content) {
        return query(id,actor,content,true).orElseThrow(AdminAudioPersistence::missing);
    }
    private Optional<ReferenceAudioUpload> query(UUID id,long actor,long content,boolean lock) {
        var q=em.createQuery("select u from ReferenceAudioUpload u where u.id=:id and u.actorId=:actor and u.contentId=:content",ReferenceAudioUpload.class)
                .setParameter("id",id).setParameter("actor",actor).setParameter("content",content);
        if(lock)q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return q.getResultStream().findFirst();
    }
    @Override public List<Audio> audios(long content) {
        return audioQuery(AUDIO+" and r.content.id=:content order by r.primary desc,r.id").setParameter("content",content)
                .getResultList().stream().map(this::audio).toList();
    }
    @Override public Optional<Audio> audio(long id) {
        return audioQuery(AUDIO+" and r.id=:id").setParameter("id",id).getResultStream().findFirst().map(this::audio);
    }
    private jakarta.persistence.TypedQuery<Object[]> audioQuery(String jpql) {
        return em.createQuery(jpql,Object[].class).setParameter("type",ContentType.ANNOUNCER).setParameter("speaker",SpeakerType.ANNOUNCER);
    }
    private Audio audio(Object[] r) {
        return new Audio((Long)r[0],(Long)r[1],(String)r[2],((SpeakerType)r[3]).name(),(Integer)r[4],Boolean.TRUE.equals(r[5]),(String)r[6],(OffsetDateTime)r[7]);
    }
    @Override public void clearPrimaries(long content) {
        em.createQuery("update ReferenceAudio r set r.primary=false where r.content.id=:id").setParameter("id",content).executeUpdate();
    }
    @Override public void makePrimary(long id) {
        em.createQuery("update ReferenceAudio r set r.primary=true where r.id=:id").setParameter("id",id).executeUpdate();
    }
    @Override public void save(ReferenceAudio audio) { em.persist(audio); }
    @Override public void advanceVersion(PracticeContent content) {
        // The parent is already write-locked. Increment immediately rather than using a
        // deferred FORCE_INCREMENT: response versions must also be current before commit.
        em.flush();
        em.createQuery("update PracticeContent c set c.version=c.version+1 where c.id=:id")
                .setParameter("id",content.getId()).executeUpdate();
        em.refresh(content);
    }
    private static BaseException missing() { return new BaseException(ErrorCode.RESOURCE_NOT_FOUND); }
}
