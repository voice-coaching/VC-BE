package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.*;
import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.admin.domain.port.*;
import org.example.voice.common.exception.*;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminReferenceAudioTransactions {
    private final AdminAccessService access;
    private final AdminStore store;
    private final AdminAudioStore audio;
    private final AdminContentInvalidation invalidation;
    private final Clock clock;

    @Transactional
    public Upload prepare(long actor,long contentId,UploadCommand command) {
        access.require(actor); var content=announcer(store.lockContent(contentId));
        AdminValidation.version(content.getVersion(),command.version());
        String extension=extension(command.fileName(),command.contentType());
        AdminValidation.text(command.reason(),500,true);
        if(command.size()<1 || command.size()>org.example.voice.admin.domain.model.AdminAudioData.MAX_BYTES
                || command.sha256()==null || !command.sha256().matches("[0-9a-f]{64}"))AdminValidation.invalid();
        OffsetDateTime now=OffsetDateTime.now(clock);
        var upload=new Upload(UUID.randomUUID(),actor,contentId,extension,command.contentType(),command.size(),command.sha256(),
                digest(content.getScriptText()),now.plusMinutes(10),null,null);
        audio.save(ReferenceAudioUpload.pending(upload,now));
        audit(actor,"REFERENCE_AUDIO_UPLOAD_REQUEST",contentId,command.reason(),"uploadId,contentType,sizeBytes,sha256");
        store.flush();return upload;
    }

    @Transactional(readOnly=true)
    public Upload read(long actor,long content,UUID uploadId) {
        access.require(actor);
        announcer(store.content(content).orElseThrow(AdminReferenceAudioTransactions::missing).type());
        return audio.upload(uploadId,actor,content).orElseThrow(AdminReferenceAudioTransactions::missing);
    }

    @Transactional(readOnly=true)
    public AudioResult replay(long actor,long content,UUID uploadId,String completionDigest) {
        var u=read(actor,content,uploadId);
        if(u.audioId()==null)return null;
        if(!Objects.equals(u.completionDigest(),completionDigest))AdminValidation.conflict();
        return new AudioResult(audio.audio(u.audioId()).orElseThrow(AdminReferenceAudioTransactions::missing),store.content(content).orElseThrow(AdminReferenceAudioTransactions::missing).version(),false);
    }

    @Transactional(readOnly=true)
    public Upload beforeVerification(long actor,long contentId,UUID uploadId,long version) {
        var upload=read(actor,contentId,uploadId);
        if(upload.audioId()==null) {
            requireLive(upload);
            var content=store.content(contentId).orElseThrow(AdminReferenceAudioTransactions::missing);
            AdminValidation.version(content.version(),version);
            if(!upload.scriptSha256().equals(digest(content.scriptText())))AdminValidation.conflict();
        }
        return upload;
    }

    @Transactional
    public AudioResult register(long actor,long contentId,UUID uploadId,long version,String speaker,boolean primary,
                                String reason,String completionDigest,VerifiedAudio verified) {
        access.require(actor); var content=announcer(store.lockContent(contentId));
        var intent=audio.lockUpload(uploadId,actor,contentId); var upload=intent.snapshot();
        if(upload.audioId()!=null) {
            if(!Objects.equals(upload.completionDigest(),completionDigest))AdminValidation.conflict();
            return new AudioResult(audio.audio(upload.audioId()).orElseThrow(AdminReferenceAudioTransactions::missing),content.getVersion(),false);
        }
        requireLive(upload); AdminValidation.version(content.getVersion(),version);
        if(!upload.scriptSha256().equals(digest(content.getScriptText())))AdminValidation.conflict();
        if(verified==null || verified.durationMs()<1 || verified.durationMs()>org.example.voice.admin.domain.model.AdminAudioData.MAX_DURATION_MS)
            throw new BaseException(ErrorCode.ADMIN_AUDIO_INVALID);
        boolean selected=primary || audio.audios(contentId).stream().noneMatch(Audio::primary);
        if(selected)audio.clearPrimaries(contentId);
        var reference=ReferenceAudio.announcer(content,speaker,verified.objectKey(),verified.durationMs(),selected,OffsetDateTime.now(clock));
        audio.save(reference);intent.complete(reference.getId(),completionDigest);audio.advanceVersion(content);
        audit(actor,"REFERENCE_AUDIO_REGISTER",reference.getId(),reason,"contentId,speakerName,speakerType,durationMs,primary");
        store.flush();invalidation.afterContentChange();
        return new AudioResult(audio.audio(reference.getId()).orElseThrow(AdminReferenceAudioTransactions::missing),content.getVersion(),true);
    }

    @Transactional
    public AudioResult primary(long actor,long audioId,long version,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var target=audio.audio(audioId).orElseThrow(AdminReferenceAudioTransactions::missing);
        var content=announcer(store.lockContent(target.contentId()));AdminValidation.version(content.getVersion(),version);
        // Another request may have retired this audio while the parent lock was being acquired.
        audio.audio(audioId).orElseThrow(AdminReferenceAudioTransactions::missing);
        audio.clearPrimaries(content.getId());audio.makePrimary(audioId);audio.advanceVersion(content);
        audit(actor,"REFERENCE_AUDIO_PRIMARY",audioId,reason,"primary");store.flush();invalidation.afterContentChange();
        return new AudioResult(audio.audio(audioId).orElseThrow(AdminReferenceAudioTransactions::missing),content.getVersion(),false);
    }
    @Transactional
    public AudioList delete(long actor,long audioId,long version,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var target=audio.audio(audioId).orElseThrow(AdminReferenceAudioTransactions::missing);
        var content=announcer(store.lockContent(target.contentId()));AdminValidation.version(content.getVersion(),version);
        target=audio.audio(audioId).orElseThrow(AdminReferenceAudioTransactions::missing);
        audio.retire(audioId,OffsetDateTime.now(clock));
        // Keep a usable representative when deleting the selected audio; never delete stored files or history.
        var remaining=audio.audios(content.getId());
        if(target.primary()&&!remaining.isEmpty()) {
            audio.clearPrimaries(content.getId());audio.makePrimary(remaining.getFirst().id());
        }
        audio.advanceVersion(content);audit(actor,"REFERENCE_AUDIO_DELETE",audioId,reason,"retiredAt,primary");
        store.flush();invalidation.afterContentChange();
        return new AudioList(audio.audios(content.getId()),content.getVersion());
    }
    @Transactional(readOnly=true)
    public AudioList list(long actor,long contentId) {
        access.require(actor);var content=store.content(contentId).orElseThrow(AdminReferenceAudioTransactions::missing);announcer(content.type());
        return new AudioList(audio.audios(contentId),content.version());
    }
    @Transactional(readOnly=true)
    public Audio forPlayback(long actor,long audioId) {
        access.require(actor);return audio.audio(audioId).orElseThrow(AdminReferenceAudioTransactions::missing);
    }
    public void requireLive(Upload upload) {
        if(!upload.expiresAt().isAfter(OffsetDateTime.now(clock)))throw new BaseException(ErrorCode.ADMIN_AUDIO_UPLOAD_EXPIRED);
    }
    private PracticeContent announcer(PracticeContent c) { announcer(c.getContentType());return c; }
    private void announcer(ContentType type) { if(type!=ContentType.ANNOUNCER)throw missing(); }
    private void audit(long actor,String action,long id,String reason,String fields) {
        store.audit(AdminAuditLog.success(actor,action,action.equals("REFERENCE_AUDIO_UPLOAD_REQUEST")?"CONTENT":"REFERENCE_AUDIO",id,reason,fields,OffsetDateTime.now(clock)));
    }
    static String extension(String name,String type) {
        AdminValidation.text(name,255,true);
        if(name.contains("/") || name.contains("\\") || name.indexOf('\0')>=0)AdminValidation.invalid();
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".m4a") && "audio/mp4".equals(type))return ".m4a";
        if(lower.endsWith(".mp3") && "audio/mpeg".equals(type))return ".mp3";
        AdminValidation.invalid();return null;
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static BaseException missing() { return new BaseException(ErrorCode.RESOURCE_NOT_FOUND); }
}
