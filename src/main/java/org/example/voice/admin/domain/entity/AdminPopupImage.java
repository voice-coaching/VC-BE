package org.example.voice.admin.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.example.voice.admin.domain.type.PopupImageState;

@Entity @Getter @Table(name="admin_popup_images") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AdminPopupImage {
    @Id private UUID id;
    @Column(nullable=false) private Long popupId;
    @Column(nullable=false,unique=true,length=255) private String objectKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private PopupImageState state;
    @Column(nullable=false) private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
    public static AdminPopupImage pending(long popup,OffsetDateTime now) {
        var image=new AdminPopupImage();image.id=UUID.randomUUID();image.popupId=popup;
        image.objectKey="popup-images/"+image.id+".png";image.state=PopupImageState.PENDING;image.createdAt=now;return image;
    }
    public void attach() { state=PopupImageState.ATTACHED; }
    public void retire() { state=PopupImageState.RETIRED; }
    public void deleted(OffsetDateTime now) { state=PopupImageState.DELETED;deletedAt=now; }
    public boolean due(OffsetDateTime now,OffsetDateTime popupEnd) {
        return state==PopupImageState.RETIRED || (state==PopupImageState.PENDING && !createdAt.plusDays(1).isAfter(now))
                || (state==PopupImageState.ATTACHED && !popupEnd.isAfter(now));
    }
}
