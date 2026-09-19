package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.CustomContentException;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.model.*;
import org.example.voice.practicecontent.domain.port.*;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CustomContentService implements CustomContentLifecycle {
    private final CustomContentRepository contents;
    private final UserReader users;
    private final PrivateTextCipher cipher;
    private final Clock clock;
    public record Input(String title,String scriptText,LearningFocus learningFocus,String retention,String locale) {}

    @Transactional public CustomContentData create(Long user,Input input,String key) {
        if(input==null || input.learningFocus()==null || !"SESSION_HISTORY".equals(input.retention()) || !"ko-KR".equals(input.locale())) invalid();
        String title=normalize(input.title(),100), script=normalize(input.scriptText(),300);
        String keyDigest=keyDigest(key);
        cipher.requireConfigured(); lock(user);
        String digest=cipher.fingerprint(title.length()+":"+title+script.length()+":"+script+":"+input.learningFocus()+":SESSION_HISTORY:ko-KR");
        if(keyDigest!=null) {
            var replay=contents.request(user,keyDigest);
            if(replay.isPresent()) {
                if(!replay.get().getRequestDigest().equals(digest)) throw new CustomContentException(409,"CONFLICT");
                return CustomContentData.from(owned(replay.get().getContentId(),user));
            }
        }
        var content=contents.save(PracticeContent.custom(user,title,script,input.learningFocus(),OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)));
        if(keyDigest!=null) contents.remember(new CustomContentRequest(user,keyDigest,digest,content.getId()));
        return CustomContentData.from(content);
    }
    public Optional<CustomContentData> find(Long id,Long user) {
        var owner=contents.owner(id);
        if(owner.isEmpty()) return Optional.empty();
        if(!owner.get().equals(user)) throw new CustomContentException(404,"RESOURCE_NOT_FOUND");
        cipher.requireConfigured();
        return Optional.of(CustomContentData.from(owned(id,user)));
    }
    @Override @Transactional public boolean validateSession(Long id,Long user,Long courseStep,Long exam) {
        var owner=contents.owner(id);
        if(owner.isEmpty()) return false;
        if(!owner.get().equals(user)) throw new CustomContentException(404,"RESOURCE_NOT_FOUND");
        lock(user); cipher.requireConfigured(); owned(id,user);
        if(courseStep!=null || exam!=null) throw new CustomContentException(409,"CONFLICT");
        return true;
    }
    @Override @Transactional public Long prepareHistoryDeletion(Long session,Long user) { lock(user); return contents.sessionContent(session,user); }
    @Override @Transactional public void afterHistoryDeletion(Long id,Long user){ contents.eraseIfUnreferenced(id,user); }
    @Override @Transactional public void eraseForUser(Long user){contents.eraseForUser(user);}
    private PracticeContent owned(Long id,Long user){return contents.owned(id,user).orElseThrow(()->new CustomContentException(404,"RESOURCE_NOT_FOUND"));}
    private void lock(Long user){var u=users.findByIdForUpdate(user).orElseThrow(()->new CustomContentException(404,"RESOURCE_NOT_FOUND"));if(u.isWithdrawn()||u.isSuspended())throw new CustomContentException(403,"FORBIDDEN");}
    private String normalize(String text,int limit) {
        if(text==null) invalid();
        text=text.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$","");
        if(text.isEmpty() || text.codePointCount(0,text.length())>limit || text.codePoints().anyMatch(c->Character.isISOControl(c) || Character.getType(c)==Character.SURROGATE)) invalid();
        return text;
    }
    private String keyDigest(String key){
        if(key==null)return null;if(!key.matches("[\\x21-\\x7e]{1,128}"))invalid();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private void invalid(){throw new CustomContentException(400,"VALIDATION_ERROR");}
}
