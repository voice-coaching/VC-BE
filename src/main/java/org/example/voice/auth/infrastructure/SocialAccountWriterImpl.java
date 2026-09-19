package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.port.SocialAccountWriter;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.entity.SocialAccount;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SocialAccountWriterImpl implements SocialAccountWriter {
    private final SocialAccountJpaRepository repository;

    @Override
    public SocialAccount save(SocialAccount socialAccount) { return repository.save(socialAccount); }
}
