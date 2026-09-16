package org.example.voice.practicecontent.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.port.CustomContentRepository;
import org.springframework.stereotype.Repository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class CustomContentPersistence implements CustomContentRepository {
    private final EntityManager em;
    private final Clock clock;
    @Override public Optional<Long> owner(Long id) {
        return em.createQuery("select c.ownerId from PracticeContent c where c.id=:id and c.ownerId is not null",Long.class)
                .setParameter("id",id).getResultStream().findFirst();
    }
    @Override public Optional<PracticeContent> owned(Long id,Long user) {
        return em.createQuery("select c from PracticeContent c where c.id=:id and c.ownerId=:user and c.customDeletedAt is null",PracticeContent.class)
                .setParameter("id",id).setParameter("user",user).getResultStream().findFirst();
    }
    @Override public Optional<CustomContentRequest> request(Long user,String key) {
        return em.createQuery("select r from CustomContentRequest r where r.userId=:user and r.keyDigest=:key",CustomContentRequest.class)
                .setParameter("user",user).setParameter("key",key).getResultStream().findFirst();
    }
    @Override public PracticeContent save(PracticeContent content) { em.persist(content); em.flush(); return content; }
    @Override public void remember(CustomContentRequest request){em.persist(request);}
    @Override public Long sessionContent(Long session,Long user) {
        return em.createQuery("select s.content.id from TrainingSession s where s.id=:id and s.userId=:user",Long.class)
                .setParameter("id",session).setParameter("user",user).getResultStream().findFirst().orElse(null);
    }
    @Override public void eraseIfUnreferenced(Long id,Long user) {
        if(id == null) return;
        if(em.createQuery("select count(s) from TrainingSession s where s.content.id=:id",Long.class).setParameter("id",id).getSingleResult()==0)
            erase("c.id=:id and c.ownerId=:user",id,user);
    }
    @Override public void eraseForUser(Long user) {
        erase("c.ownerId=:user",null,user);
        em.createQuery("update AnalysisRequestOutbox o set o.privatePayload=null where o.analysisResult.recording.trainingSession.userId=:user and o.privatePayload is not null")
                .setParameter("user",user).executeUpdate();
    }
    private void erase(String condition,Long id,Long user) {
        var query=em.createQuery("update PracticeContent c set c.customScript=null,c.customTitle=null,c.customDeletedAt=:now,c.updatedAt=:now where "+condition+" and c.customDeletedAt is null")
                .setParameter("now",OffsetDateTime.now(clock)).setParameter("user",user);
        if(id!=null) query.setParameter("id",id); query.executeUpdate();
        // Keep tombstones for deleted history so replay cannot recreate erased text.
        if(id==null) em.createQuery("delete from CustomContentRequest r where r.userId=:user").setParameter("user",user).executeUpdate();
    }
}
