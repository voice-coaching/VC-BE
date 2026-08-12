package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.user.domain.type.UserRole;

public interface TokenProvider {
    IssuedTokens issue(Long userId, UserRole role);
    TokenClaims parseAccessToken(String token);
    TokenClaims parseRefreshToken(String token);
    long accessTokenSeconds();
}
