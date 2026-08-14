package org.example.voice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.user.domain.port.LoginProviderReader;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class LoginProviderReaderImpl implements LoginProviderReader {

    private final SocialAccountJpaRepository socialAccountJpaRepository;

    @Override
    public List<String> findByUserId(Long userId) {
        return socialAccountJpaRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .map(account -> account.getProvider().name())
                .distinct()
                .toList();
    }
}
