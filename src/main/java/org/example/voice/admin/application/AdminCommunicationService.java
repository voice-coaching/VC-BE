package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import org.example.voice.admin.domain.entity.*;
import org.example.voice.admin.domain.model.AdminData.Page;
import org.example.voice.admin.domain.model.AdminCommunicationData.*;
import org.example.voice.admin.domain.port.*;
import org.example.voice.admin.domain.type.*;
import org.example.voice.common.exception.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminCommunicationService {
    private final AdminAccessService access;
    private final AdminCommunicationStore communications;
    private final AdminStore store;
    private final Clock clock;
    private final PopupImageReader popupImages;
    public Page<CampaignView> campaigns(long actor,int page,int size) {
        access.require(actor);AdminValidation.page(page,size);var p=communications.campaigns(page,size);
        return new Page<>(p.items().stream().map(this::view).toList(),page,size,p.totalElements());
    }
    public CampaignView campaign(long actor,long id) { access.require(actor);return view(campaign(id,false)); }
    @Transactional public CampaignView create(long actor,CampaignDraft d,String reason) {
        access.require(actor);reason(reason);var unique=validateCampaign(d);
        var c=AdminNotificationCampaign.create(d.audience(),d.kind(),d.title(),d.body(),d.deepLink(),unique,actor,now());
        communications.save(c);audit(actor,"NOTIFICATION_CREATE","NOTIFICATION_CAMPAIGN",c.getId(),reason,"audience,kind,title,body,deepLink,recipients");
        store.flush();return view(c);
    }
    @Transactional public CampaignView edit(long actor,long id,long version,CampaignDraft d,String reason) {
        access.require(actor);reason(reason);var c=campaign(id,true);requireDraft(c);AdminValidation.version(c.getVersion(),version);
        var recipients=validateCampaign(d);c.edit(d.audience(),d.kind(),d.title(),d.body(),d.deepLink(),recipients);
        audit(actor,"NOTIFICATION_EDIT","NOTIFICATION_CAMPAIGN",id,reason,"audience,kind,title,body,deepLink,recipients");
        store.flush();return view(c);
    }
    @Transactional public void delete(long actor,long id,long version,String reason) {
        access.require(actor);reason(reason);var c=campaign(id,true);requireDraft(c);AdminValidation.version(c.getVersion(),version);
        communications.delete(c);audit(actor,"NOTIFICATION_DELETE","NOTIFICATION_CAMPAIGN",id,reason,"deleted");store.flush();
    }
    private void requireDraft(AdminNotificationCampaign campaign) { if(campaign.getSentAt()!=null)AdminValidation.conflict(); }
    private Set<Long> validateCampaign(CampaignDraft d) {
        if(d==null||d.audience()==null||d.kind()==null)AdminValidation.invalid();
        message(d.title(),d.body(),d.deepLink(),true);
        List<Long> ids=d.userIds()==null?List.of():d.userIds();
        if(ids.size()>500||ids.stream().anyMatch(id->id==null||id<1)||new HashSet<>(ids).size()!=ids.size())AdminValidation.invalid();
        if((d.audience()==AdminAudience.ALL_ACTIVE&&!ids.isEmpty())||(d.audience()==AdminAudience.SELECTED_USERS&&ids.isEmpty()))AdminValidation.invalid();
        Set<Long> unique=new HashSet<>(ids);
        if(d.audience()==AdminAudience.SELECTED_USERS&&communications.activeRecipients(unique,false)!=ids.size())AdminValidation.invalid();
        return unique;
    }
    @Transactional public CampaignView dispatch(long actor,long id,long version,String reason) {
        access.require(actor);reason(reason);var c=campaign(id,true);
        // Repeating the same command after a lost response must not deliver a second notification.
        if(c.getSentAt()!=null)return view(c);
        AdminValidation.version(c.getVersion(),version);
        var now=now();int delivered=communications.deliver(c,now);c.sent(delivered,now);
        audit(actor,"NOTIFICATION_DISPATCH","NOTIFICATION_CAMPAIGN",id,reason,"sentAt,sentCount");
        store.flush();return view(c);
    }
    public Page<PopupView> popups(long actor,int page,int size) {
        access.require(actor);AdminValidation.page(page,size);var p=communications.popups(page,size);
        return new Page<>(p.items().stream().map(this::view).toList(),page,size,p.totalElements());
    }
    public PopupView popup(long actor,long id) { access.require(actor);return view(popup(id,false)); }
    @Transactional public PopupView createPopup(long actor,PopupDraft d,String reason) {
        access.require(actor);validate(d,reason);var p=AdminPopup.create(actor,now());edit(p,d);communications.save(p);
        audit(actor,"POPUP_CREATE","POPUP",p.getId(),reason,"title,body,deepLink,priority,startsAt,endsAt,enabled");store.flush();return view(p);
    }
    @Transactional public PopupView editPopup(long actor,long id,long version,PopupDraft d,String reason) {
        access.require(actor);validate(d,reason);var p=popup(id,true);AdminValidation.version(p.getVersion(),version);edit(p,d);
        audit(actor,"POPUP_EDIT","POPUP",id,reason,"title,body,deepLink,priority,startsAt,endsAt,enabled");store.flush();return view(p);
    }
    public PopupView activePopup(long userId) {
        var user=store.user(userId).orElseThrow(AdminCommunicationService::missing);
        if(!user.status().equals(org.example.voice.user.domain.type.UserStatus.ACTIVE.name())||communications.activeRecipients(Set.of(userId),false)!=1)throw new BaseException(ErrorCode.ACCESS_DENIED);
        return communications.activePopup(userId,now()).map(this::view).orElse(null);
    }
    private AdminNotificationCampaign campaign(long id,boolean lock) { return communications.campaign(id,lock).orElseThrow(AdminCommunicationService::missing); }
    private AdminPopup popup(long id,boolean lock) { return communications.popup(id,lock).orElseThrow(AdminCommunicationService::missing); }
    private CampaignView view(AdminNotificationCampaign c) {
        boolean sent=c.getSentAt()!=null;
        return new CampaignView(c.getId(),c.getVersion(),c.getAudience(),c.getKind(),c.getTitle(),c.getBody(),c.getDeepLink(),
                c.getUserIds().stream().sorted().toList(),sent?"SENT":"DRAFT",
                sent?c.getSentCount():communications.activeRecipients(c.getUserIds(),c.getAudience()==AdminAudience.ALL_ACTIVE),
                c.getSentCount(),c.getCreatedAt(),c.getSentAt());
    }
    private PopupView view(AdminPopup p) { return new PopupView(p.getId(),p.getVersion(),p.getTitle(),p.getBody(),p.getDeepLink(),p.getPriority(),p.getStartsAt(),p.getEndsAt(),p.isEnabled(),p.getCreatedAt(),popupImages.image(p.getId(),p.getEndsAt())); }
    private void validate(PopupDraft d,String reason) {
        reason(reason);if(d==null)AdminValidation.invalid();message(d.title(),d.body(),d.deepLink(),false);
        if(d.startsAt()==null||d.startsAt().getYear()<2000||d.startsAt().getYear()>2200||d.durationDays()<1||d.durationDays()>365||d.priority()<0||d.priority()>100)AdminValidation.invalid();
    }
    private void edit(AdminPopup p,PopupDraft d) { p.edit(d.title(),d.body(),d.deepLink(),d.priority(),d.startsAt(),d.durationDays(),d.enabled()); }
    private void message(String title,String body,String link,boolean required) {
        AdminValidation.text(title,200,true);AdminValidation.text(body,1000,true);
        if(title.matches("(?s).*<[^>]+>.*")||body.matches("(?s).*<[^>]+>.*"))AdminValidation.invalid();
        if((required&&link==null)||(link!=null&&!Set.of("/home","/notifications","/mypage").contains(link)))AdminValidation.invalid();
    }
    private void reason(String reason) { AdminValidation.text(reason,500,true); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private void audit(long actor,String action,String type,long id,String reason,String fields) { store.audit(AdminAuditLog.success(actor,action,type,id,reason,fields,now())); }
    private static BaseException missing() { return new BaseException(ErrorCode.RESOURCE_NOT_FOUND); }
}
