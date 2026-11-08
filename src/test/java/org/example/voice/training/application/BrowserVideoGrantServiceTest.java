package org.example.voice.training.application;

import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.common.exception.BaseException;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.training.controller.dto.BrowserVideoGrantRequest;
import org.example.voice.training.domain.port.BrowserVideoSessionReader;
import org.example.voice.training.infrastructure.BrowserVideoGrantSigner;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserVideoGrantServiceTest {
    TokenProvider tokens=mock(TokenProvider.class);
    RefreshTokenReader logins=mock(RefreshTokenReader.class);
    BrowserVideoSessionReader sessions=mock(BrowserVideoSessionReader.class);
    ProcessingConsentLedger consents=mock(ProcessingConsentLedger.class);
    BrowserVideoGrantSigner signer=mock(BrowserVideoGrantSigner.class);
    BrowserVideoGrantService service;
    BrowserVideoGrantRequest request;

    @BeforeEach void setup() {
        service=new BrowserVideoGrantService(tokens,logins,sessions,consents,signer,true,
                "backend-test","ai-test","https://ai.example","https://frontend.example");
        request=new BrowserVideoGrantRequest(UUID.randomUUID().toString(),
                new BrowserVideoGrantRequest.Source("a".repeat(64),123L,"video/webm"),
                true,"voice-video-processing-consent-v1");
        when(tokens.parseAccessToken("signed-access")).thenReturn(new TokenClaims(7L,UserRole.USER,"login-session",OffsetDateTime.now().plusHours(1)));
        when(logins.isActiveSession(eq(7L),eq("login-session"),any())).thenReturn(true);
        when(sessions.ownedSource(12L,7L)).thenReturn(new BrowserVideoSessionReader.Source(11L,"서버 원고"));
        when(signer.sign(any())).thenReturn("test-grant");
    }

    @Test void signedGrantBindsOwnedServerScriptAndOriginal() {
        var r=service.issue(12L,"Bearer signed-access","https://frontend.example",request);
        assertEquals("서버 원고",r.metadata().scriptText());
        assertEquals(request.source(),r.metadata().source());
        assertEquals("test-grant",r.grantToken());
        ArgumentCaptor<Map<String,Object>> capture=ArgumentCaptor.forClass(Map.class);
        verify(signer).sign(capture.capture());
        var c=capture.getValue();
        assertEquals("7",c.get("sub")); assertEquals(12L,c.get("sessionId"));
        assertEquals(300L,(long)c.get("exp")-(long)c.get("iat"));
        assertEquals("video:create upload read cancel",c.get("scope"));
        assertEquals(request.clientRequestId(),c.get("clientRequestId"));
        verify(consents).grantFaceVideoProcessing(eq(7L),eq(12L),eq("voice-video-processing-consent-v1"),eq("browser-original:"+"a".repeat(64)));
    }

    @Test void missingBearerDoesNotFallBackToCompatibilityIdentity() {
        assertThrows(BaseException.class,()->service.issue(12L,null,"https://frontend.example",request));
        verifyNoInteractions(sessions,signer);
    }

    @Test void revokedLoginCannotIssueGrant() {
        when(logins.isActiveSession(eq(7L),eq("login-session"),any())).thenReturn(false);
        assertThrows(BaseException.class,()->service.issue(12L,"Bearer signed-access","https://frontend.example",request));
        verifyNoInteractions(sessions,signer);
    }

    @Test void originOrMissingConsentCannotIssueGrant() {
        assertThrows(BaseException.class,()->service.issue(12L,"Bearer signed-access","https://evil.example",request));
        var missing=new BrowserVideoGrantRequest(request.clientRequestId(),request.source(),false,null);
        assertThrows(BaseException.class,()->service.issue(12L,"Bearer signed-access","https://frontend.example",missing));
        verifyNoInteractions(signer,consents);
    }

    @Test void sourceLimitIsEnforcedBeforeSigning() {
        var bad=new BrowserVideoGrantRequest(request.clientRequestId(),
                new BrowserVideoGrantRequest.Source("a".repeat(64),256L*1024*1024+1,"video/webm"),true,"voice-video-processing-consent-v1");
        assertThrows(BaseException.class,()->service.issue(12L,"Bearer signed-access","https://frontend.example",bad));
        verifyNoInteractions(signer,consents);
    }
}
