package org.example.voice.admin.infrastructure;

import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.admin.domain.port.AdminAudioStorage;
import org.example.voice.common.exception.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(prefix="storage",name="enabled",havingValue="false",matchIfMissing=true)
public class UnavailableAdminAudioStorage implements AdminAudioStorage {
    @Override public void assertAvailable() { throw unavailable(); }
    @Override public SignedUpload sign(Upload upload) { throw unavailable(); }
    @Override public VerifiedAudio verifyAndStore(Upload upload) { throw unavailable(); }
    @Override public Playback playback(Audio audio) { throw unavailable(); }
    private BaseException unavailable() { return new BaseException(ErrorCode.ADMIN_AUDIO_UNAVAILABLE); }
}
