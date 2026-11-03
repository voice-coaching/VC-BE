package org.example.voice.admin.domain.port;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.admin.domain.entity.*;
import org.example.voice.admin.domain.model.AdminData.Page;
public interface AdminCommunicationStore {
    void save(AdminNotificationCampaign campaign);
    Optional<AdminNotificationCampaign> campaign(long id,boolean lock);
    Page<AdminNotificationCampaign> campaigns(int page,int size);
    long activeRecipients(Set<Long> ids,boolean all);
    int deliver(AdminNotificationCampaign campaign,OffsetDateTime now);
    void save(AdminPopup popup);
    Optional<AdminPopup> popup(long id,boolean lock);
    Page<AdminPopup> popups(int page,int size);
    Optional<AdminPopup> activePopup(long userId,OffsetDateTime now);
}
