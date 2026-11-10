package org.example.voice.scriptlibrary.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.scriptlibrary.domain.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @RequiredArgsConstructor
public class ScriptLibraryPersistence implements ScriptLibraryRepository {
    private final EntityManager em;
    public List<SavedScript> scripts(Long user) {
        return em.createQuery("select c,e.topicId from ScriptLibraryEntry e,PracticeContent c where e.contentId=c.id and e.userId=:user and c.ownerId=:user and c.customDeletedAt is null order by e.createdAt desc,c.id desc",Object[].class)
                .setParameter("user",user).getResultList().stream().map(row->new SavedScript((PracticeContent)row[0],(Long)row[1])).toList();
    }
    public List<ScriptLibraryTopic> topics(Long user){return em.createQuery("select t from ScriptLibraryTopic t where t.userId=:user order by t.id",ScriptLibraryTopic.class).setParameter("user",user).getResultList();}
    public Optional<ScriptLibraryEntry> entry(Long user,Long id){return em.createQuery("select e from ScriptLibraryEntry e where e.userId=:user and e.contentId=:id",ScriptLibraryEntry.class).setParameter("user",user).setParameter("id",id).getResultStream().findFirst();}
    public Optional<ScriptLibraryTopic> topic(Long user,Long id){return em.createQuery("select t from ScriptLibraryTopic t where t.userId=:user and t.id=:id",ScriptLibraryTopic.class).setParameter("user",user).setParameter("id",id).getResultStream().findFirst();}
    public Optional<ScriptLibraryRequest> request(Long user,String key){return em.createQuery("select r from ScriptLibraryRequest r where r.userId=:user and r.keyDigest=:key",ScriptLibraryRequest.class).setParameter("user",user).setParameter("key",key).getResultStream().findFirst();}
    public boolean topicNameExists(Long user,String digest,Long excluding){return em.createQuery("select count(t) from ScriptLibraryTopic t where t.userId=:user and t.nameDigest=:digest and (:excluding is null or t.id<>:excluding)",Long.class).setParameter("user",user).setParameter("digest",digest).setParameter("excluding",excluding).getSingleResult()>0;}
    public void add(ScriptLibraryEntry entry){em.persist(entry);em.flush();}
    public void add(ScriptLibraryTopic topic){em.persist(topic);em.flush();}
    public void remember(ScriptLibraryRequest request){em.persist(request);}
    public void remove(ScriptLibraryEntry entry){em.remove(entry);em.flush();}
    public void remove(ScriptLibraryTopic topic){em.remove(topic);em.flush();}
    public long size(Long user){return em.createQuery("select count(e) from ScriptLibraryEntry e where e.userId=:user",Long.class).setParameter("user",user).getSingleResult();}
}
