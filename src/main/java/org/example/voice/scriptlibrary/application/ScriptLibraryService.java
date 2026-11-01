package org.example.voice.scriptlibrary.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.CustomContentException;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.model.CustomContentData;
import org.example.voice.practicecontent.domain.port.*;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.scriptlibrary.domain.*;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ScriptLibraryService {
    private final ScriptLibraryRepository library;
    private final CustomContentRepository contents;
    private final UserReader users;
    private final PrivateTextCipher cipher;
    private final Clock clock;
    public record Input(String title,String scriptText,Long topicId) {}
    public record Topic(Long id,String name) {static Topic from(ScriptLibraryTopic t){return new Topic(t.getId(),t.getName().value());}}
    public record Library(List<CustomContentData> items,List<Topic> topics,Map<String,String> assignments) {}

    public Library list(Long user) {
        lock(user);
        var topics=library.topics(user).stream().map(Topic::from).toList();
        var names=new HashMap<Long,String>();topics.forEach(t->names.put(t.id(),t.name()));
        var assignments=new LinkedHashMap<String,String>();
        var items=library.scripts(user).stream().map(item->{
            assignments.put(item.content().getId().toString(),names.getOrDefault(item.topicId(),"미분류"));
            return CustomContentData.from(item.content());
        }).toList();
        return new Library(items,topics,assignments);
    }
    public CustomContentData save(Long user,Long id,Input input,String key) {
        if(input==null)throw invalid();
        String title=normalize(input.title(),100),script=normalize(input.scriptText(),300);
        String keyHash=keyHash(key);
        lock(user);
        String digest=cipher.fingerprint("library:"+id+":"+input.topicId()+":"+title.length()+":"+title+script.length()+":"+script);
        var replay=library.request(user,keyHash);
        if(replay.isPresent()) {
            if(!replay.get().getRequestDigest().equals(digest))throw new CustomContentException(409,"CONFLICT");
            entry(user,replay.get().getContentId());
            return CustomContentData.from(owned(user,replay.get().getContentId()));
        }
        ScriptLibraryEntry previous=id==null?null:entry(user,id);
        if(input.topicId()!=null)topic(user,input.topicId());
        if(id==null && library.size(user)>=500)throw new CustomContentException(409,"SCRIPT_LIBRARY_LIMIT");
        // Every edit is an immutable practice-content revision. Existing analyses keep their original source.
        var now=OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var content=contents.save(PracticeContent.custom(user,title,script,LearningFocus.PRONUNCIATION,now));
        library.add(new ScriptLibraryEntry(content.getId(),user,input.topicId(),now));
        library.remember(new ScriptLibraryRequest(user,keyHash,digest,content.getId()));
        if(previous!=null){library.remove(previous);contents.eraseIfUnreferenced(id,user);}
        return CustomContentData.from(content);
    }
    public void delete(Long user,List<Long> ids) {
        if(ids==null||ids.isEmpty()||ids.size()>500||ids.stream().anyMatch(id->id==null||id<=0))throw invalid();
        lock(user);
        // Resolve every owner before making changes, so mixed-owner bulk requests are atomic.
        var entries=ids.stream().distinct().map(id->entry(user,id)).toList();
        entries.forEach(e->{library.remove(e);contents.eraseIfUnreferenced(e.getContentId(),user);});
    }
    public Topic createTopic(Long user,String name) {
        name=normalize(name,10);if(name.equals("미분류"))throw invalid();lock(user);
        String digest=cipher.fingerprint("library-topic:"+name);
        if(library.topicNameExists(user,digest,null))throw new CustomContentException(409,"TOPIC_NAME_CONFLICT");
        if(library.topics(user).size()>=30)throw new CustomContentException(409,"SCRIPT_TOPIC_LIMIT");
        var topic=new ScriptLibraryTopic(user,name,digest);library.add(topic);return Topic.from(topic);
    }
    public Topic renameTopic(Long user,Long id,String name) {
        name=normalize(name,10);if(name.equals("미분류"))throw invalid();lock(user);
        var topic=topic(user,id);String digest=cipher.fingerprint("library-topic:"+name);
        if(library.topicNameExists(user,digest,id))throw new CustomContentException(409,"TOPIC_NAME_CONFLICT");
        topic.rename(name,digest);return Topic.from(topic);
    }
    public void deleteTopic(Long user,Long id) {
        lock(user);var topic=topic(user,id);
        library.scripts(user).stream().filter(s->id.equals(s.topicId())).map(s->s.content().getId()).toList()
                .forEach(content->{library.remove(entry(user,content));contents.eraseIfUnreferenced(content,user);});
        library.remove(topic);
    }
    private void lock(Long user){cipher.requireConfigured();var account=users.findByIdForUpdate(user).orElseThrow(()->new CustomContentException(404,"RESOURCE_NOT_FOUND"));if(account.isWithdrawn()||account.isSuspended())throw new CustomContentException(403,"FORBIDDEN");}
    private ScriptLibraryEntry entry(Long user,Long id){return library.entry(user,id).orElseThrow(()->new CustomContentException(404,"RESOURCE_NOT_FOUND"));}
    private ScriptLibraryTopic topic(Long user,Long id){return library.topic(user,id).orElseThrow(()->new CustomContentException(404,"RESOURCE_NOT_FOUND"));}
    private PracticeContent owned(Long user,Long id){return contents.owned(id,user).orElseThrow(()->new CustomContentException(404,"RESOURCE_NOT_FOUND"));}
    private String normalize(String text,int limit){
        if(text==null)throw invalid();text=text.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$","");
        if(text.isEmpty()||text.codePointCount(0,text.length())>limit||text.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.SURROGATE))throw invalid();
        return text;
    }
    private String keyHash(String key){
        if(key==null||!key.matches("[\\x21-\\x7e]{1,128}"))throw invalid();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private CustomContentException invalid(){return new CustomContentException(400,"VALIDATION_ERROR");}
}
