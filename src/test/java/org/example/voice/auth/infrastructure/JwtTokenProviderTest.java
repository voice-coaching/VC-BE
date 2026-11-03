package org.example.voice.auth.infrastructure;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {
    private static final String SECRET="test-secret-test-secret-test-secret-1234";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET,"voice-test",600,1200);

    @Test void issuesSignedTypedAccessAndRefreshTokensWithExistingClaims() throws Exception {
        var issued=provider.issue(7L,UserRole.USER);
        for(String raw:List.of(issued.accessToken(),issued.refreshToken())) {
            var token=SignedJWT.parse(raw);
            assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
            assertThat(token.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
            assertThat(token.getSignature().toString()).isNotBlank();
            var claims=token.getJWTClaimsSet();
            assertThat(claims.getSubject()).isEqualTo("7");assertThat(claims.getIssuer()).isEqualTo("voice-test");
            assertThat(claims.getStringClaim("sid")).isEqualTo(issued.sessionId());
            assertThat(claims.getStringClaim("role")).isEqualTo("USER");assertThat(claims.getJWTID()).isNotBlank();
        }
        assertThat(provider.parseAccessToken(issued.accessToken()).userId()).isEqualTo(7L);
        assertThat(provider.parseRefreshToken(issued.refreshToken()).role()).isEqualTo(UserRole.USER);
    }
    @Test void refreshRotatesBothCredentialsWithinTheSelectedLoginSession() {
        var original=provider.issue(7L,UserRole.USER);var renewed=provider.reissue(7L,UserRole.USER,original.sessionId());
        assertThat(renewed.accessToken()).isNotEqualTo(original.accessToken());assertThat(renewed.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(provider.parseAccessToken(renewed.accessToken()).sessionId()).isEqualTo(original.sessionId());
        assertThat(provider.parseRefreshToken(renewed.refreshToken()).sessionId()).isEqualTo(original.sessionId());
    }
    @Test void rejectsCredentialTypeSubstitutionInBothDirections() {
        var issued=provider.issue(7L,UserRole.USER);
        rejects(()->provider.parseAccessToken(issued.refreshToken()),ErrorCode.UNAUTHORIZED);
        rejects(()->provider.parseRefreshToken(issued.accessToken()),ErrorCode.INVALID_REFRESH_TOKEN);
    }
    @Test void rejectsUnsignedAccessAndRefreshTokensEvenWithPlausibleClaims() {
        for(String type:List.of("access","refresh")) {
            var raw=new PlainJWT(claims(type)).serialize();
            if(type.equals("access"))rejects(()->provider.parseAccessToken(raw),ErrorCode.UNAUTHORIZED);
            else rejects(()->provider.parseRefreshToken(raw),ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
    @Test void rejectsPayloadTamperingWhileRetainingTheOriginalSignature() throws Exception {
        var issued=provider.issue(7L,UserRole.USER);var parts=issued.accessToken().split("\\.");
        var changed=new JWTClaimsSet.Builder(SignedJWT.parse(issued.accessToken()).getJWTClaimsSet()).subject("8").claim("role","ADMIN").build();
        var encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(changed.toString().getBytes(StandardCharsets.UTF_8));
        var tampered=parts[0]+"."+encoded+"."+parts[2];
        rejects(()->provider.parseAccessToken(tampered),ErrorCode.UNAUTHORIZED);
    }
    @Test void rejectsATokenSignedWithAnotherKey() {
        var other=new JwtTokenProvider("other-secret-other-secret-other-secret-1234","voice-test",600,1200).issue(7L,UserRole.USER);
        rejects(()->provider.parseAccessToken(other.accessToken()),ErrorCode.UNAUTHORIZED);
        rejects(()->provider.parseRefreshToken(other.refreshToken()),ErrorCode.INVALID_REFRESH_TOKEN);
    }
    @Test void rejectsAnotherIssuerAndUnapprovedHmacAlgorithm() throws Exception {
        var issuer=new JwtTokenProvider(SECRET,"another-issuer",600,1200).issue(7L,UserRole.USER);
        rejects(()->provider.parseAccessToken(issuer.accessToken()),ErrorCode.UNAUTHORIZED);
        var hs384=new SignedJWT(new JWSHeader(JWSAlgorithm.HS384),claims("access"));
        hs384.sign(new MACSigner((SECRET+SECRET).getBytes(StandardCharsets.UTF_8)));
        rejects(()->provider.parseAccessToken(hs384.serialize()),ErrorCode.UNAUTHORIZED);
    }
    @Test void rejectsExpiredAccessAndMapsExpiredRefreshToExistingErrorCode() throws Exception {
        String access=signed(new JWTClaimsSet.Builder(claims("access")).issueTime(Date.from(Instant.now().minusSeconds(900))).expirationTime(Date.from(Instant.now().minusSeconds(300))).build());
        String refresh=signed(new JWTClaimsSet.Builder(claims("refresh")).issueTime(Date.from(Instant.now().minusSeconds(900))).expirationTime(Date.from(Instant.now().minusSeconds(300))).build());
        rejects(()->provider.parseAccessToken(access),ErrorCode.UNAUTHORIZED);
        rejects(()->provider.parseRefreshToken(refresh),ErrorCode.REFRESH_TOKEN_EXPIRED);
    }
    @Test void rejectsMissingRequiredClaimsAndInvalidSubjects() throws Exception {
        var cases=List.of(new JWTClaimsSet.Builder(claims("access")).expirationTime(null).build(),
                new JWTClaimsSet.Builder(claims("access")).claim("sid",null).build(),
                new JWTClaimsSet.Builder(claims("access")).claim("sid","").build(),
                new JWTClaimsSet.Builder(claims("access")).claim("role",null).build(),
                new JWTClaimsSet.Builder(claims("access")).subject("0").build(),
                new JWTClaimsSet.Builder(claims("access")).subject("not-a-user").build());
        for(var value:cases){String raw=signed(value);rejects(()->provider.parseAccessToken(raw),ErrorCode.UNAUTHORIZED);}
    }
    @Test void rejectsMalformedTokensAndMissingCredentials() {
        for(String raw:Arrays.asList(null,"","invalid","a.b.c")) {
            rejects(()->provider.parseAccessToken(raw),ErrorCode.UNAUTHORIZED);
            rejects(()->provider.parseRefreshToken(raw),ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
    @Test void refusesEmptyOrShortSigningSecretsAndInvalidIssuanceIdentity() {
        for(String secret:Arrays.asList(null,"","short"))assertThatThrownBy(()->new JwtTokenProvider(secret,"voice-test",600,1200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->provider.issue(0L,UserRole.USER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->provider.issue(7L,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->provider.reissue(7L,UserRole.USER,"")).isInstanceOf(IllegalArgumentException.class);
    }
    private JWTClaimsSet claims(String type) {
        return new JWTClaimsSet.Builder().issuer("voice-test").subject("7").issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).jwtID(UUID.randomUUID().toString())
                .claim("role","USER").claim("sid","test-session").claim("type",type).build();
    }
    private String signed(JWTClaimsSet claims) throws Exception {
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));return jwt.serialize();
    }
    private void rejects(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(InvalidTokenException.class,error->assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
