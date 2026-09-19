package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.port.SocialAccountReader;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SocialAccountReaderImpl implements SocialAccountReader {
    private final SocialAccountJpaRepository repository;

    @Override
    public Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId) {
        return repository.findByProviderAndProviderUserId(provider, providerUserId);
    }
}
