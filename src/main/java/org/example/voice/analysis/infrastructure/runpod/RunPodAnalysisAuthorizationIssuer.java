package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodAnalysisAuthorizationIssuer implements AnalysisAuthorizationIssuer {

    private static final String KEY_ID = "runpod-http";
    private static final Duration INTERNAL_GRANT_TTL = Duration.ofMinutes(10);

    private final Clock clock;

    public RunPodAnalysisAuthorizationIssuer() {
        this(Clock.systemUTC());
    }

    RunPodAnalysisAuthorizationIssuer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public AnalysisAuthorizationGrant issue(AnalysisAuthorizationIssue issue) {
        Instant issuedAt = clock.instant();
        return new AnalysisAuthorizationGrant(
                AnalysisAuthorizationGrant.LEGACY_GRANT_VERSION,
                KEY_ID,
                issue.requestEventId(),
                issue.analysisId(),
                issue.contentId(),
                issue.promptRevision(),
                issue.scriptSha256(),
                sha256(issue.audioObjectKey()),
                issue.audioSha256(),
                issue.mimeType(),
                issue.fileSizeBytes(),
                issue.durationMs(),
                issue.learningFocus(),
                issue.consentReceiptSha256(),
                issue.consentPolicyRevision(),
                issue.visualInput() == null ? null : sha256(issue.visualInput().objectKey()),
                issue.visualInput() == null ? null : issue.visualInput().sha256(),
                issue.visualInput() == null ? null : issue.visualInput().mimeType(),
                issue.visualInput() == null ? null : issue.visualInput().fileSizeBytes(),
                issue.visualInput() == null ? null : issue.visualInput().consentReceiptSha256(),
                issue.visualInput() == null ? null : issue.visualInput().consentPolicyRevision(),
                null,
                issuedAt,
                issuedAt.plus(INTERNAL_GRANT_TTL),
                AnalysisAuthorizationGrant.PURPOSE,
                AnalysisAuthorizationGrant.DATA_CATEGORY,
                true,
                false,
                sha256("runpod-http:" + issue.requestEventId())
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
