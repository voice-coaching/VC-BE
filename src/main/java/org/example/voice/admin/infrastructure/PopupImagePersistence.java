package org.example.voice.admin.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import java.util.*;
import java.time.OffsetDateTime;
import org.example.voice.admin.domain.entity.AdminPopupImage;
import org.example.voice.admin.domain.port.PopupImageStore;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class PopupImagePersistence implements PopupImageStore {
    private final EntityManager em;
    public void save(AdminPopupImage image) { em.persist(image); }
    public Optional<AdminPopupImage> find(UUID id) {
        var image=em.find(AdminPopupImage.class,id);
        if(image!=null)em.refresh(image);
        return Optional.ofNullable(image);
    }
    public Optional<AdminPopupImage> attached(long popup) {
        return em.createQuery("select i from AdminPopupImage i where i.popupId=:id and i.state='ATTACHED'",AdminPopupImage.class)
                .setParameter("id",popup).getResultStream().findFirst();
    }
    public List<UUID> due(OffsetDateTime now) {
        return em.createQuery("select i.id from AdminPopupImage i, AdminPopup p where p.id=i.popupId and "
                +"(i.state='RETIRED' or (i.state='PENDING' and i.createdAt<=:old) or (i.state='ATTACHED' and p.endsAt<=:now)) order by i.createdAt,i.id",UUID.class)
                .setParameter("old",now.minusDays(1)).setParameter("now",now).setMaxResults(20).getResultList();
    }
}
