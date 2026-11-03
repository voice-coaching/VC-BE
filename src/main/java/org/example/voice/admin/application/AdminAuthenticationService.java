package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.type.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuthenticationService {
    private final TokenProvider tokens;
    private final RefreshTokenReader sessions;
    private final AdminAccessService access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TokenClaims authenticate(String token, boolean adminRequired) {
        if (token == null || token.isBlank()) throw new BaseException(ErrorCode.UNAUTHORIZED);
        TokenClaims claims = tokens.parseAccessToken(token);
        if (!sessions.isActiveSession(claims.userId(), claims.sessionId(), OffsetDateTime.now(clock)))
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        if (adminRequired) {
            if (claims.role() != UserRole.ADMIN) throw new BaseException(ErrorCode.ACCESS_DENIED);
            access.require(claims.userId());
        }
        return claims;
    }
}
