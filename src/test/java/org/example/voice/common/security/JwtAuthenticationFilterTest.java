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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final TokenProvider tokens = mock(TokenProvider.class);
    private final RefreshTokenReader sessions = mock(RefreshTokenReader.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, sessions, new ObjectMapper());

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
