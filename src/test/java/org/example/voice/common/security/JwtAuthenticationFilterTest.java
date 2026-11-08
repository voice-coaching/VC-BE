package org.example.voice.common.security;

import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.runpod.RunPodInternalAuthentication;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.user.domain.type.UserRole;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final TokenProvider tokens = mock(TokenProvider.class);
    private final RefreshTokenReader sessions = mock(RefreshTokenReader.class);
    private final java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2030-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, sessions, new ObjectMapper(), clock);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void missingTokenAndIdentityHeaderNeverManufactureAnAuthenticatedUser() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/users/me");request.addHeader("X-User-Id","42");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
        verifyNoInteractions(tokens,sessions);
    }

    @Test void validCredentialsUseVerifiedSubjectAndRoleThenClearTheContext() throws Exception {
        valid();var request=request("/api/users/me","valid");request.addHeader("X-User-Id","999");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{
            var auth=SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.isAuthenticated()).isTrue();assertThat(auth.getPrincipal()).isEqualTo(new LoginUser(42L));
            assertThat(auth.getAuthorities().stream().map(a->a.getAuthority())).containsExactly("ROLE_USER");
        });
        verify(sessions).isActiveSession(42L,"current",OffsetDateTime.now(clock));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void terminatedSessionAndExpiredClaimsReturnUnauthorizedWithoutCallingTheHandler() throws Exception {
        when(tokens.parseAccessToken("valid")).thenReturn(claims(OffsetDateTime.now(clock).plusMinutes(1)));
        rejected(request("/api/users/me","valid"),401,"UNAUTHORIZED");
        when(tokens.parseAccessToken("valid")).thenReturn(claims(OffsetDateTime.now(clock)));
        rejected(request("/api/users/me","valid"),401,"UNAUTHORIZED");
        verify(sessions,times(1)).isActiveSession(any(),anyString(),any());
    }

    @Test void invalidSchemeEmptyBearerAndDuplicateHeadersAreRejectedBeforeTokenParsing() throws Exception {
        for(String header:new String[]{"Basic credential","Bearer ","invalid"}){
            var request=new MockHttpServletRequest("GET","/api/users/me");request.addHeader("Authorization",header);
            rejected(request,401,"UNAUTHORIZED");
        }
        var duplicate=request("/api/users/me","first");duplicate.addHeader("Authorization","Bearer second");
        rejected(duplicate,401,"UNAUTHORIZED");verifyNoInteractions(tokens,sessions);
    }

    @Test void databaseOutageReturnsRetryable503AndNeverAuthenticates() throws Exception {
        when(tokens.parseAccessToken("valid")).thenReturn(claims(OffsetDateTime.now(clock).plusMinutes(1)));
        for(RuntimeException failure:new RuntimeException[]{new org.springframework.dao.DataAccessResourceFailureException("private diagnostic"),
                new org.springframework.transaction.CannotCreateTransactionException("private diagnostic"),
                new org.example.voice.auth.exception.SessionStoreUnavailableException()}) {
            doThrow(failure).when(sessions).isActiveSession(any(),anyString(),any());
            var response=rejected(request("/api/users/me","valid"),503,"AUTH_SESSION_STORE_UNAVAILABLE");
            assertThat(response.getHeader("Retry-After")).isEqualTo("1");
            assertThat(response.getContentAsString()).doesNotContain("private diagnostic");
        }
    }

    @Test void downstreamErrorsAreNotReclassifiedAsAuthenticationFailures() {
        valid();var failure=new IllegalStateException("handler failed");
        assertThatThrownBy(()->filter.doFilter(request("/api/users/me","valid"),new MockHttpServletResponse(),(req,res)->{throw failure;}))
                .isSameAs(failure);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void asyncRedispatchRevalidatesTheSession() throws Exception {
        valid();var request=request("/api/users/me","valid");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{});
        request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        when(sessions.isActiveSession(any(),anyString(),any())).thenReturn(false);
        rejected(request,401,"UNAUTHORIZED");verify(sessions,times(2)).isActiveSession(any(),anyString(),any());
    }

    @Test void publicEndpointsAndDedicatedAuthenticationPathsIgnoreUserCredentials() throws Exception {
        for(String path:new String[]{"/api/auth/login","/api/auth/signup","/api/auth/social-login","/api/auth/token/refresh","/api/payments/webhooks/portone"}) {
            var reached=new AtomicBoolean();filter.doFilter(request(path,"stale-user-token"),new MockHttpServletResponse(),(req,res)->reached.set(true));
            assertThat(reached).isTrue();
        }
        for(String path:new String[]{"/api/auth/email-availability","/v3/api-docs","/swagger-ui/index.html","/internal/actuator/health","/api/browser-video/.well-known/upload-grant-keys"}) {
            var request=new MockHttpServletRequest("GET",path);request.addHeader("Authorization","Bearer stale-user-token");
            var reached=new AtomicBoolean();filter.doFilter(request,new MockHttpServletResponse(),(req,res)->reached.set(true));assertThat(reached).isTrue();
        }
        verifyNoInteractions(tokens,sessions);
    }

    private void valid(){
        when(tokens.parseAccessToken("valid")).thenReturn(claims(OffsetDateTime.now(clock).plusMinutes(1)));
        when(sessions.isActiveSession(42L,"current",OffsetDateTime.now(clock))).thenReturn(true);
    }
    private TokenClaims claims(OffsetDateTime expiry){return new TokenClaims(42L,UserRole.USER,"current",expiry);}
    private MockHttpServletResponse rejected(MockHttpServletRequest request,int status,String code) throws Exception {
        var response=new MockHttpServletResponse();var reached=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->reached.set(true));
        assertThat(reached).isFalse();assertThat(response.getStatus()).isEqualTo(status);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).path("code").asText()).isEqualTo(code);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();return response;
    }

    @Test
    void callbacksReachTheirDedicatedAuthenticationWithoutJwtParsing() throws Exception {
        RunPodAnalysisProperties properties = new RunPodAnalysisProperties();
        properties.setCallbackToken("test-service-token");
        RunPodInternalAuthentication authentication = new RunPodInternalAuthentication(properties);
        for (String action : new String[]{"claim", "heartbeat", "result"}) {
            MockHttpServletRequest request = request("/api/internal/ai/analyses/1/" + action, "test-service-token");
            AtomicBoolean reached = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                authentication.verify(request.getHeader("Authorization"));
                reached.set(true);
            });
            assertThat(reached).isTrue();
        }
        verifyNoInteractions(tokens, sessions);
        assertThatThrownBy(() -> authentication.verify("Bearer wrong-token")).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> authentication.verify(null)).isInstanceOf(BaseException.class);
    }

    @Test
    void publicAndSimilarlyNamedPathsStillRejectInvalidJwt() throws Exception {
        when(tokens.parseAccessToken("test-service-token")).thenThrow(new InvalidTokenException(ErrorCode.UNAUTHORIZED));
        for (String path : new String[]{"/api/analysis-capabilities", "/api/internal/ai-other/analyses/1/claim"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean reached = new AtomicBoolean();
            filter.doFilter(request(path, "test-service-token"), response, (req, res) -> reached.set(true));
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(reached).isFalse();
        }
        verify(tokens, times(2)).parseAccessToken("test-service-token");
        verifyNoInteractions(sessions);
    }

    private MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
