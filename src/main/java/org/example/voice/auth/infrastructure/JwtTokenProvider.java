package org.example.voice.auth.infrastructure;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.type.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider {

    private final String issuer;
    private final long accessSeconds;
    private final long refreshSeconds;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${auth.jwt.secret:}") String secret,
            @Value("${auth.jwt.issuer}") String issuer,
            @Value("${auth.jwt.access-token-seconds}") long accessSeconds,
            @Value("${auth.jwt.refresh-token-seconds}") long refreshSeconds
    ) {
        {}

        this.issuer = issuer;
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
        this.clock = Clock.systemUTC();
    }

    @Override
    public IssuedTokens issue(Long userId, UserRole role) {
        return reissue(userId, role, UUID.randomUUID().toString());
    }

    @Override
    public IssuedTokens reissue(Long userId, UserRole role, String sessionId) {

        Instant now = clock.instant();
        return new IssuedTokens(
                encode(userId, role, sessionId, "access", now, now.plusSeconds(accessSeconds)),
                encode(userId, role, sessionId, "refresh", now, now.plusSeconds(refreshSeconds)),
                sessionId,
                OffsetDateTime.ofInstant(now.plusSeconds(refreshSeconds), ZoneOffset.UTC)
        );
    }

    private String encode(Long userId, UserRole role, String sessionId, String type, Instant issuedAt, Instant expiresAt) {
        return new com.nimbusds.jwt.PlainJWT(new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(issuer).subject(userId.toString())
                .issueTime(java.util.Date.from(issuedAt)).expirationTime(java.util.Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString()).claim("role",role.name()).claim("sid",sessionId).claim("type",type)
                .build()).serialize();
    }

    @Override
    public TokenClaims parseAccessToken(String token) { return parse(token, "access", ErrorCode.UNAUTHORIZED); }

    @Override
    public TokenClaims parseRefreshToken(String token) { return parse(token, "refresh", ErrorCode.INVALID_REFRESH_TOKEN); }

    private TokenClaims parse(String token, String expectedType, ErrorCode errorCode) {
        try {
            var claims = com.nimbusds.jwt.JWTParser.parse(token).getJWTClaimsSet();
            var expires = claims.getExpirationTime();
            String role = claims.getStringClaim("role");
            return new TokenClaims(Long.valueOf(claims.getSubject()), role == null ? UserRole.USER : UserRole.valueOf(role),
                    claims.getStringClaim("sid"), expires == null ? null : OffsetDateTime.ofInstant(expires.toInstant(), ZoneOffset.UTC));
        } catch (java.text.ParseException | IllegalArgumentException exception) {
            throw new InvalidTokenException(errorCode, exception);
        }
    }

    @Override
    public long accessTokenSeconds() { return accessSeconds; }
}
