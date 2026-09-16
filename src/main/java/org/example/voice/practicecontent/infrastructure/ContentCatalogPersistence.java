package org.example.voice.practicecontent.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.ContentCatalogException;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.model.*;
import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.port.ContentCatalogReader;
import org.example.voice.practicecontent.domain.type.*;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Repository @RequiredArgsConstructor
public class ContentCatalogPersistence implements ContentCatalogReader {
    private static final String ORDER=" order by case when c.publishedAt is null then 1 else 0 end asc,c.publishedAt desc,c.createdAt desc,c.id desc";
    private static final String REVERSE=" order by case when c.publishedAt is null then 1 else 0 end desc,c.publishedAt asc,c.createdAt asc,c.id asc";
    private final EntityManager em;
    private final Clock clock;

    @Override public PracticeContentPageData<PracticeContentSummaryData> list(Filter filter,int page,int size) {
        if((long)page*size>Integer.MAX_VALUE)throw new ContentCatalogException(400,"VALIDATION_ERROR");
        OffsetDateTime now=OffsetDateTime.now(clock);
        var contents=bind(em.createQuery("select c from PracticeContent c"+where(filter)+ORDER,PracticeContent.class),filter,now)
                .setFirstResult(page*size).setMaxResults(size).getResultList();
        long count=bind(em.createQuery("select count(c) from PracticeContent c"+where(filter),Long.class),filter,now).getSingleResult();
        Map<Long,String> speakers=speakers(contents);
        var items=contents.stream().map(c->summary(c,speakers.get(c.getId()))).toList();
        return PracticeContentPageData.of(items,page,size,count);
    }

    @Override public Facets facets(ContentType type) {
        Filter filter=new Filter(type,null,null,null);OffsetDateTime now=OffsetDateTime.now(clock);
        var counts=bind(em.createQuery("select c.category,count(c) from PracticeContent c"+where(filter)+" and c.category is not null and trim(c.category)<>'' group by c.category",Object[].class),filter,now).getResultList();
        Map<String,ContentCategory> taxonomy=new HashMap<>();
        em.createQuery("select t from ContentCategory t where t.contentType=:type",ContentCategory.class)
                .setParameter("type",type).getResultList().forEach(t->taxonomy.put(t.getCode(),t));
        var categories=counts.stream().map(row->{
            String code=(String)row[0];var term=taxonomy.get(code);
            return new Facet(code,term==null?code:term.getLabel(),(Long)row[1],term==null?Integer.MAX_VALUE:term.getSortOrder());
        }).sorted(Comparator.comparingInt(Facet::order).thenComparing(Facet::value)).toList();
        var difficulties=bind(em.createQuery("select c.difficulty,count(c) from PracticeContent c"+where(filter)+" group by c.difficulty",Object[].class),filter,now)
                .getResultList().stream().map(row->{
                    Difficulty difficulty=(Difficulty)row[0];
                    String label=switch(difficulty){case BEGINNER->"초급";case INTERMEDIATE->"중급";case ADVANCED->"고급";};
                    return new Facet(difficulty.name(),label,(Long)row[1],difficulty.ordinal()+1);
                }).sorted(Comparator.comparingInt(Facet::order)).toList();
        return new Facets(type,categories,difficulties,revision(type,categories,difficulties));
    }

    @Override public Adjacent adjacent(Long id,Filter filter) {
        OffsetDateTime now=OffsetDateTime.now(clock);
        PracticeContent anchor=bind(em.createQuery("select c from PracticeContent c"+where(filter)+" and c.id=:id",PracticeContent.class),filter,now)
                .setParameter("id",id).getResultStream().findFirst().orElseThrow(()->new ContentCatalogException(404,"RESOURCE_NOT_FOUND"));
        return new Adjacent(neighbor(anchor,filter,now,true),neighbor(anchor,filter,now,false));
    }

    private Neighbor neighbor(PracticeContent anchor,Filter filter,OffsetDateTime now,boolean previous) {
        String op=previous?">":"<";
        String tail="(c.createdAt "+op+" :created or (c.createdAt=:created and c.id "+op+" :anchor))";
        String condition;
        if(anchor.getPublishedAt()==null)
            condition=previous?"(c.publishedAt is not null or (c.publishedAt is null and "+tail+"))":"(c.publishedAt is null and "+tail+")";
        else
            condition="(c.publishedAt "+op+" :published or (c.publishedAt=:published and "+tail+")"+(previous?"":" or c.publishedAt is null")+")";
        var query=bind(em.createQuery("select c.id,c.title from PracticeContent c"+where(filter)+" and "+condition+(previous?REVERSE:ORDER),Object[].class),filter,now)
                .setParameter("created",anchor.getCreatedAt()).setParameter("anchor",anchor.getId()).setMaxResults(1);
        if(anchor.getPublishedAt()!=null)query.setParameter("published",anchor.getPublishedAt());
        return query.getResultStream().findFirst().map(row->new Neighbor((Long)row[0],(String)row[1])).orElse(null);
    }

    private String where(Filter filter) {
        return " where c.status=org.example.voice.practicecontent.domain.type.PublishStatus.PUBLISHED and c.ownerId is null and (c.publishedAt is null or c.publishedAt<=:now)"
                +(filter.type()==null?"":" and c.contentType=:type")
                +(category(filter)?" and c.category=:category":"")
                +(filter.difficulty()==null?"":" and c.difficulty=:difficulty")
                +(filter.focus()==null?"":" and c.learningFocus=:focus");
    }
    private <T> TypedQuery<T> bind(TypedQuery<T> query,Filter filter,OffsetDateTime now) {
        query.setParameter("now",now);
        if(filter.type()!=null)query.setParameter("type",filter.type());
        if(category(filter))query.setParameter("category",filter.category());
        if(filter.difficulty()!=null)query.setParameter("difficulty",filter.difficulty());
        if(filter.focus()!=null)query.setParameter("focus",filter.focus());
        return query;
    }
    private boolean category(Filter filter){return filter.category()!=null && !filter.category().isBlank();}
    private Map<Long,String> speakers(List<PracticeContent> contents) {
        var ids=contents.stream().filter(c->c.getContentType()==ContentType.ANNOUNCER && (c.getSpeakerName()==null || c.getSpeakerName().isBlank())).map(PracticeContent::getId).toList();
        if(ids.isEmpty())return Map.of();
        Map<Long,String> result=new HashMap<>();
        em.createQuery("select a.content.id,a.speakerName from ReferenceAudio a where a.content.id in :ids and a.speakerName is not null and trim(a.speakerName)<>'' order by a.primary desc,a.id asc",Object[].class)
                .setParameter("ids",ids).getResultList().forEach(row->result.putIfAbsent((Long)row[0],(String)row[1]));
        return result;
    }
    private PracticeContentSummaryData summary(PracticeContent c,String fallbackSpeaker) {
        var metadata=ContentTextMetadata.from(c.getScriptText());
        boolean news=c.getContentType()==ContentType.NEWS, sentence=c.getContentType()==ContentType.SENTENCE;
        String speaker=c.getContentType()!=ContentType.ANNOUNCER?null:c.getSpeakerName()==null||c.getSpeakerName().isBlank()?fallbackSpeaker:c.getSpeakerName();
        return new PracticeContentSummaryData(c.getId(),c.getContentType(),c.getTitle(),c.getCategory(),c.getDifficulty(),c.getEstimatedSeconds(),c.getScriptText(),
                news?c.getPublisher():null,news?metadata.paragraphCount():null,news?metadata.sentenceCount():null,sentence?metadata.syllableCount():null,
                c.getPublishedAt()==null?null:c.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC),speaker);
    }
    private String revision(ContentType type,List<Facet> categories,List<Facet> difficulties) {
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            for(String part:List.of(type.name(),categories.toString(),difficulties.toString())){
                byte[] value=part.getBytes(StandardCharsets.UTF_8);hash.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());hash.update(value);
            }
            return HexFormat.of().formatHex(hash.digest());
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
