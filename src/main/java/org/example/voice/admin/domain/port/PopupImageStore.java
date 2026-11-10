package org.example.voice.admin.domain.port;

import java.util.*;
import java.time.OffsetDateTime;
import org.example.voice.admin.domain.entity.AdminPopupImage;

public interface PopupImageStore {
    void save(AdminPopupImage image);
    Optional<AdminPopupImage> find(UUID id);
    Optional<AdminPopupImage> attached(long popupId);
    List<UUID> due(OffsetDateTime now);
}
