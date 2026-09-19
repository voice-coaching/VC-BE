package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.type.OAuthProvider;
import java.util.Optional;

public interface SocialAccountReader {
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
