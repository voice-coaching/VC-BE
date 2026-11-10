package org.example.voice.admin.application;

import java.time.*;
import org.example.voice.admin.domain.type.PopupImageState;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.*;
import org.example.voice.admin.domain.port.*;
import org.example.voice.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PopupImageTransactions {
    private final AdminAccessService access;
    private final AdminCommunicationStore popups;
    private final PopupImageStore images;
    private final AdminStore audit;
    private final PopupImageStorage storage;
    private final Clock clock;
    public record Reservation(UUID id,String objectKey) {}
    @Transactional
    public Reservation reserve(long actor,long popup,long version,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        if(version<0)AdminValidation.invalid();
        var p=popup(popup);AdminValidation.version(p.getVersion(),version);
        if(!p.getEndsAt().isAfter(now()))AdminValidation.conflict();
        var image=AdminPopupImage.pending(popup,now());images.save(image);audit.flush();
        return new Reservation(image.getId(),image.getObjectKey());
    }
    @Transactional
    public void attach(long actor,long popup,long version,UUID imageId,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var p=popup(popup);AdminValidation.version(p.getVersion(),version);
        if(!p.getEndsAt().isAfter(now()))AdminValidation.conflict();
        var image=images.find(imageId).orElseThrow(this::missing);
        if(image.getPopupId()!=popup || image.getState()!=PopupImageState.PENDING || !image.getCreatedAt().plusDays(1).isAfter(now()))AdminValidation.conflict();
        images.attached(popup).ifPresent(AdminPopupImage::retire);
        audit.flush(); // Retire before attaching: the database permits one live image per popup.
        image.attach();p.imageChanged();
        record(actor,popup,"POPUP_IMAGE_ATTACH",reason);audit.flush();
    }
    @Transactional
    public void remove(long actor,long popup,long version,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var p=popup(popup);AdminValidation.version(p.getVersion(),version);
        images.attached(popup).ifPresent(AdminPopupImage::retire);p.imageChanged();
        record(actor,popup,"POPUP_IMAGE_REMOVE",reason);audit.flush();
    }
    @Transactional(readOnly=true)
    public List<UUID> due(){return images.due(now());}
    @Transactional
    public void cleanup(UUID imageId) {
        var candidate=images.find(imageId).orElse(null);if(candidate==null)return;
        var p=popup(candidate.getPopupId());
        var current=images.find(imageId).orElseThrow(this::missing);
        if(!current.due(now(),p.getEndsAt()))return;
        // The popup lock serializes deletion with period extension, replacement and detach.
        storage.delete(current.getObjectKey());current.deleted(now());audit.flush();
    }
    private AdminPopup popup(long id){return popups.popup(id,true).orElseThrow(this::missing);}
    private OffsetDateTime now(){return OffsetDateTime.now(clock);}
    private BaseException missing(){return new BaseException(ErrorCode.RESOURCE_NOT_FOUND);}
    private void record(long actor,long popup,String action,String reason){
        audit.audit(AdminAuditLog.success(actor,action,"POPUP",popup,reason,"image",now()));
    }
}
