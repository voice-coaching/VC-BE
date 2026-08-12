package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.port.RefreshTokenWriter;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.entity.RefreshToken;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenWriterImpl implements RefreshTokenWriter {
    private final RefreshTokenJpaRepository repository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) { return repository.save(refreshToken); }

    @Override
    public void delete(RefreshToken refreshToken) { repository.delete(refreshToken); }

    @Override
    public void deleteByUserId(Long userId) { repository.deleteByUserId(userId); }
}
