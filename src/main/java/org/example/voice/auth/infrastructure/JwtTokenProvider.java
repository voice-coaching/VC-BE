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
import java.util.Locale;

@Component
/** Issues only signed HS256 credentials; unsigned migration-era JWTs are not accepted. */
public class JwtTokenProvider implements TokenProvider {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final long accessSeconds;
    private final long refreshSeconds;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.issuer}") String issuer,
            @Value("${auth.jwt.access-token-seconds}") long accessSeconds,
            @Value("${auth.jwt.refresh-token-seconds}") long refreshSeconds
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder verifier = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        verifier.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.decoder = verifier;
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
        if (userId == null || userId <= 0 || role == null) {
            throw new IllegalArgumentException("A valid token subject and role are required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Login session ID is required");
        }
        Instant now = clock.instant();
        return new IssuedTokens(
                encode(userId, role, sessionId, "access", now, now.plusSeconds(accessSeconds)),
                encode(userId, role, sessionId, "refresh", now, now.plusSeconds(refreshSeconds)),
                sessionId,
                OffsetDateTime.ofInstant(now.plusSeconds(refreshSeconds), ZoneOffset.UTC)
        );
    }

    private String encode(Long userId, UserRole role, String sessionId, String type, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(userId.toString()).issuedAt(issuedAt).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("role", role.name()).claim("sid", sessionId).claim("type", type).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public TokenClaims parseAccessToken(String token) { return parse(token, "access", ErrorCode.UNAUTHORIZED); }

    @Override
    public TokenClaims parseRefreshToken(String token) { return parse(token, "refresh", ErrorCode.INVALID_REFRESH_TOKEN); }

    private TokenClaims parse(String token, String expectedType, ErrorCode errorCode) {
        try {
            Jwt jwt = decoder.decode(token);
            String sessionId = jwt.getClaimAsString("sid");
            if (!expectedType.equals(jwt.getClaimAsString("type")) || jwt.getExpiresAt() == null
                    || sessionId == null || sessionId.isBlank()) {
                throw new InvalidTokenException(errorCode);
            }
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) throw new InvalidTokenException(errorCode);
            return new TokenClaims(userId, UserRole.valueOf(jwt.getClaimAsString("role")), sessionId,
                    OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC));
        } catch (JwtValidationException exception) {
            ErrorCode mapped = "refresh".equals(expectedType) && exception.getErrors().stream()
                    .anyMatch(error -> error.getDescription().toLowerCase(Locale.ROOT).contains("expired"))
                    ? ErrorCode.REFRESH_TOKEN_EXPIRED : errorCode;
            throw new InvalidTokenException(mapped, exception);
        } catch (InvalidTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidTokenException(errorCode, exception);
        }
    }

    @Override
    public long accessTokenSeconds() { return accessSeconds; }
}
