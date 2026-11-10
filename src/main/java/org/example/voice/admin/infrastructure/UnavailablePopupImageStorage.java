package org.example.voice.admin.infrastructure;

import java.time.Duration;
import org.example.voice.admin.domain.port.PopupImageStorage;
import org.example.voice.common.exception.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(prefix="storage",name="enabled",havingValue="false",matchIfMissing=true)
public class UnavailablePopupImageStorage implements PopupImageStorage {
    private BaseException unavailable(){return new BaseException(ErrorCode.ADMIN_STORE_UNAVAILABLE);}
    public void upload(String key,byte[] png){throw unavailable();}
    public void delete(String key){throw unavailable();}
    public String url(String key,Duration lifetime){throw unavailable();}
}
