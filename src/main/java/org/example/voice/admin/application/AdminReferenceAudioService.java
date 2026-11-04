package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.admin.domain.port.AdminAudioStorage;
import org.example.voice.common.exception.*;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Storage and decoder I/O run outside database transactions and content locks. */
@Service @RequiredArgsConstructor
public class AdminReferenceAudioService {
    private final AdminReferenceAudioTransactions transactions;
    private final AdminAccessService access;
    private final AdminAudioStorage storage;
    private final Semaphore verificationCapacity=new Semaphore(2);
    public UploadReceipt issue(long actor,long content,UploadCommand command) {
        access.require(actor);storage.assertAvailable();
        var intent=transactions.prepare(actor,content,command);
        var signed=storage.sign(intent);
        return new UploadReceipt(intent.id(),signed.url(),signed.headers(),signed.expiresAt().isBefore(intent.expiresAt())?signed.expiresAt():intent.expiresAt());
    }
    public AudioResult complete(long actor,long content,UUID upload,long version,String speaker,boolean primary,String reason) {
        AdminValidation.text(speaker,255,true);AdminValidation.text(reason,500,true);
        String fingerprint=AdminReferenceAudioTransactions.digest(speaker.length()+":"+speaker+":"+primary+":"+reason);
        var existing=transactions.replay(actor,content,upload,fingerprint);if(existing!=null)return existing;
        var intent=transactions.beforeVerification(actor,content,upload,version);
        if(intent.audioId()!=null)return transactions.replay(actor,content,upload,fingerprint);
        if(!verificationCapacity.tryAcquire())throw new BaseException(ErrorCode.ADMIN_AUDIO_UNAVAILABLE);
        try {
            var verified=storage.verifyAndStore(intent);
            return transactions.register(actor,content,upload,version,speaker,primary,reason,fingerprint,verified);
        } finally { verificationCapacity.release(); }
    }
    public Playback playback(long actor,long audioId) { return storage.playback(transactions.forPlayback(actor,audioId)); }
}
