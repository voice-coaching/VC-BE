package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
