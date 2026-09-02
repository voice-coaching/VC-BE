package org.example.voice.analysis.infrastructure.authorization;

import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class HmacAnalysisAuthorizationIssuer implements AnalysisAuthorizationIssuer {
    private static final Duration MAXIMUM_TTL = Duration.ofMinutes(10);

    private final AnalysisAuthorizationProperties properties;
    private final byte[] signingSecret;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public HmacAnalysisAuthorizationIssuer(AnalysisAuthorizationProperties properties) {
        this(properties, Clock.systemUTC(), new SecureRandom());
    }

    HmacAnalysisAuthorizationIssuer(
            AnalysisAuthorizationProperties properties,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.signingSecret = validateAndDecode(properties);
    }

    @Override
    public AnalysisAuthorizationGrant issue(AnalysisAuthorizationIssue issue) {
        if (issue == null || !properties.getConsentPolicyRevision().equals(issue.consentPolicyRevision())) {
            throw new BaseException(ErrorCode.ANALYSIS_CONSENT_POLICY_MISMATCH);
        }
        Instant issuedAt = clock.instant();
        byte[] receiptEntropy = new byte[32];
        secureRandom.nextBytes(receiptEntropy);
        AnalysisAuthorizationGrant unsigned = grant(issue, issuedAt, sha256(receiptEntropy), "0".repeat(64));
        return grant(issue, issuedAt, unsigned.consentReceiptSha256(), hmac(unsigned.canonicalSigningInput()));
    }

    private AnalysisAuthorizationGrant grant(
            AnalysisAuthorizationIssue issue,
            Instant issuedAt,
            String receiptSha256,
            String signature
    ) {
        return new AnalysisAuthorizationGrant(
                AnalysisAuthorizationGrant.GRANT_VERSION,
                properties.getKeyId(),
                issue.requestEventId(),
                issue.analysisId(),
                issue.contentId(),
                issue.promptRevision(),
                issue.scriptSha256(),
                sha256(issue.audioObjectKey().getBytes(StandardCharsets.UTF_8)),
                issue.audioSha256(),
                issue.mimeType(),
                issue.fileSizeBytes(),
                issue.durationMs(),
                issue.learningFocus(),
                receiptSha256,
                issue.consentPolicyRevision(),
                issuedAt,
                issuedAt.plus(properties.getGrantTtl()),
                AnalysisAuthorizationGrant.PURPOSE,
                AnalysisAuthorizationGrant.DATA_CATEGORY,
                true,
                false,
                signature
        );
    }

    private String hmac(byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (Exception error) {
            throw new IllegalStateException("analysis_authorization_signing_unavailable", error);
        }
    }

    private static byte[] validateAndDecode(AnalysisAuthorizationProperties properties) {
        if (properties.getKeyId() == null
                || !properties.getKeyId().matches("[A-Za-z0-9._-]{1,100}")
                || properties.getConsentPolicyRevision() == null
                || properties.getConsentPolicyRevision().isBlank()
                || properties.getConsentPolicyRevision().length() > 100
                || properties.getGrantTtl() == null
                || properties.getGrantTtl().isZero()
                || properties.getGrantTtl().isNegative()
                || properties.getGrantTtl().compareTo(MAXIMUM_TTL) > 0) {
            throw new IllegalStateException("analysis_authorization_configuration_invalid");
        }
        try {
            byte[] secret = Base64.getDecoder().decode(properties.getSigningSecretBase64());
            if (secret.length < 32) {
                throw new IllegalStateException("analysis_authorization_configuration_invalid");
            }
            return secret;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalStateException("analysis_authorization_configuration_invalid", error);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
