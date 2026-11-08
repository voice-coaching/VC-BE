package org.example.voice.training.application;

import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.training.controller.dto.BrowserVideoGrantRequest;
import org.example.voice.training.controller.dto.BrowserVideoGrantResponse;
import org.example.voice.training.domain.model.RecordingMediaPolicy;
import org.example.voice.training.domain.port.BrowserVideoSessionReader;
import org.example.voice.training.domain.port.BrowserVideoGrantEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class BrowserVideoGrantService {
    private final TokenProvider tokens;
    private final RefreshTokenReader logins;
    private final BrowserVideoSessionReader sessions;
    private final ProcessingConsentLedger consents;
    private final BrowserVideoGrantEncoder signer;
    private final String issuer, audience, aiBaseUrl;
    private final List<String> allowedOrigins;
    private final boolean enabled;

    public BrowserVideoGrantService(TokenProvider tokens, RefreshTokenReader logins,
            BrowserVideoSessionReader sessions, ProcessingConsentLedger consents, BrowserVideoGrantEncoder signer,
            @Value("${ai.browser-video.enabled:false}") boolean enabled,
            @Value("${ai.browser-video.issuer:}") String issuer,
            @Value("${ai.browser-video.audience:}") String audience,
            @Value("${ai.browser-video.ai-base-url:}") String aiBaseUrl,
            @Value("${ai.browser-video.allowed-origins:}") String origins) {
        this.tokens=tokens; this.logins=logins; this.sessions=sessions; this.consents=consents; this.signer=signer;
        this.enabled=enabled; this.issuer=issuer; this.audience=audience; this.aiBaseUrl=aiBaseUrl;
        this.allowedOrigins=Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (enabled && (issuer.isBlank() || audience.isBlank() || !originValid(aiBaseUrl)
                || allowedOrigins.isEmpty() || allowedOrigins.stream().anyMatch(s -> !originValid(s))))
            throw new IllegalStateException("Browser video grant configuration is invalid");
    }

    @Transactional
    public BrowserVideoGrantResponse issue(Long sessionId, String authorization, String origin,
                                            BrowserVideoGrantRequest request) {
        if (!enabled) throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        // Explicit signed access-token authentication; never trust X-User-Id or
        // the legacy compatibility principal for this authorization endpoint.
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        var login = tokens.parseAccessToken(authorization.substring(7));
        if (!logins.isActiveSession(login.userId(),login.sessionId(),OffsetDateTime.now()))
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        if (!allowedOrigins.contains(origin)) throw new BaseException(ErrorCode.ACCESS_DENIED);
        if (request == null || request.source() == null || sessionId == null || sessionId <= 0)
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        try {
            if (!UUID.fromString(request.clientRequestId()).toString().equals(request.clientRequestId()))
                throw new IllegalArgumentException();
        } catch (RuntimeException error) { throw new BaseException(ErrorCode.VALIDATION_ERROR); }
        var source = request.source();
        if (source.sha256() == null || !source.sha256().matches("[a-f0-9]{64}")
                || source.fileSizeBytes() == null || source.fileSizeBytes() <= 0 || source.fileSizeBytes() > 256L*1024*1024
                || !("video/mp4".equals(source.mimeType()) || "video/webm".equals(source.mimeType())
                     || "video/quicktime".equals(source.mimeType())))
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        var selected = sessions.ownedSource(sessionId, login.userId());
        if (!Boolean.TRUE.equals(request.videoProcessingConsentAccepted())
                || !RecordingMediaPolicy.VIDEO_CONSENT_POLICY_REVISION.equals(request.videoProcessingConsentPolicyRevision()))
            throw new BaseException(ErrorCode.VIDEO_PROCESSING_CONSENT_REQUIRED);
        consents.grantFaceVideoProcessing(login.userId(),sessionId,request.videoProcessingConsentPolicyRevision(),
                "browser-original:" + source.sha256());
        long now = Instant.now().getEpochSecond(), expires = now + 300;
        var claims = new LinkedHashMap<String,Object>();
        claims.put("iss",issuer); claims.put("aud",audience); claims.put("sub",login.userId().toString());
        claims.put("jti",UUID.randomUUID().toString()); claims.put("iat",now); claims.put("nbf",now); claims.put("exp",expires);
        claims.put("scope","video:create upload read cancel"); claims.put("origin",origin);
        claims.put("sessionId",sessionId); claims.put("clientRequestId",request.clientRequestId());
        claims.put("contentId",selected.contentId()); claims.put("source",source);
        try {
            claims.put("scriptSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(selected.scriptText().getBytes(StandardCharsets.UTF_8))));
        } catch (Exception error) { throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR); }
        return new BrowserVideoGrantResponse(BrowserVideoGrantEncoder.TYPE,aiBaseUrl,
                "/browser/v1/video-analysis-jobs",signer.sign(claims),expires,
                new BrowserVideoGrantResponse.UploadMetadata(request.clientRequestId(),selected.scriptText(),selected.contentId(),source));
    }

    private static boolean originValid(String s) {
        try {
            var u=URI.create(s);
            return "https".equals(u.getScheme()) && u.getHost()!=null && u.getRawUserInfo()==null
                    && (u.getRawPath()==null || u.getRawPath().isEmpty()) && u.getRawQuery()==null && u.getRawFragment()==null;
        } catch (RuntimeException error) { return false; }
    }
}
