package org.example.voice.common.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenProvider tokenProvider;
    private final RefreshTokenReader refreshTokenReader;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/email-availability") || path.equals("/api/auth/signup")
                || path.equals("/api/auth/login") || path.equals("/api/auth/social-login")
                || path.equals("/api/auth/token/refresh") || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TokenClaims claims = tokenProvider.parseAccessToken(authorization.substring(7));
            if (!refreshTokenReader.isActiveSession(claims.userId(), claims.sessionId(), OffsetDateTime.now(ZoneOffset.UTC))) {
                throw new InvalidTokenException(ErrorCode.UNAUTHORIZED);
            }
            LoginUser principal = new LoginUser(claims.userId());
            var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (InvalidTokenException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ErrorCode.UNAUTHORIZED.getMessage()));
        }
    }
}
