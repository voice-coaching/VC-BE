package org.example.voice.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null);

    @Test
    void allowsConfiguredOriginPreflightWithCredentials() throws Exception {
        CorsConfiguration configuration = configurationFor(
                "https://vc-fe.vercel.app,http://localhost:3000");
        MockHttpServletRequest request = preflightRequest("https://vc-fe.vercel.app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(new DefaultCorsProcessor().processRequest(configuration, request, response)).isTrue();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("https://vc-fe.vercel.app");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
    }

    @Test
    void rejectsUnconfiguredOriginPreflight() throws Exception {
        CorsConfiguration configuration = configurationFor("https://vc-fe.vercel.app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(new DefaultCorsProcessor().processRequest(
                configuration, preflightRequest("https://malicious.example"), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    private CorsConfiguration configurationFor(String allowedOrigins) {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(allowedOrigins);
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/auth/login"));
    }

    private MockHttpServletRequest preflightRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");
        return request;
    }
}
