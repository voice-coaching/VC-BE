package org.example.voice.auth.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.entity.RefreshToken;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.domain.port.RefreshTokenWriter;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final TokenProvider tokenProvider;
    private final RefreshTokenReader refreshTokenReader;
    private final RefreshTokenWriter refreshTokenWriter;
    private final UserReader userReader;

    @Transactional
    public IssuedTokens issueSession(User user) {
        User lockedUser = userReader.findByIdForUpdate(user.getId()).orElse(user);
        IssuedTokens issued = tokenProvider.issue(lockedUser.getId(), lockedUser.getRole());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshToken stored = refreshTokenReader.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> RefreshToken.issue(user.getId(), hash(issued.refreshToken()), issued.sessionId(), issued.refreshExpiresAt(), now));
        stored.rotate(hash(issued.refreshToken()), issued.sessionId(), issued.refreshExpiresAt(), now);
        refreshTokenWriter.save(stored);
        return issued;
    }

    @Transactional
    public IssuedTokens rotate(String rawRefreshToken) {
        TokenClaims claims = tokenProvider.parseRefreshToken(rawRefreshToken);
        User user = userReader.findByIdForUpdate(claims.userId())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN));
        RefreshToken stored = refreshTokenReader.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (stored.isExpired(now)) {
            refreshTokenWriter.delete(stored);
            throw new InvalidTokenException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        if (!stored.getUserId().equals(claims.userId())) {
            throw new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return issueSession(user);
    }

    @Transactional
    public void revoke(Long userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            refreshTokenWriter.deleteByUserId(userId);
            return;
        }
        refreshTokenReader.findByTokenHashForUpdate(hash(rawRefreshToken))
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(refreshTokenWriter::delete);
    }

    public long accessTokenSeconds() { return tokenProvider.accessTokenSeconds(); }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
