package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
    List<SocialAccount> findAllByUserIdOrderByIdAsc(Long userId);
}
