package org.example.voice.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.auth.exception.SessionStoreUnavailableException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.response.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Collections;
import java.time.Clock;
import java.time.OffsetDateTime;

/** Accept signed access credentials only after validating the current login session. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenProvider tokenProvider;
    private final org.example.voice.auth.domain.port.RefreshTokenReader refreshTokenReader;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final Clock clock;

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return UserAuthenticationRoutes.exempt(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) {
            // The authorization boundary returns 401 for protected paths. Never manufacture an identity.
            chain.doFilter(request, response);
            return;
        }
        String authorization = headers.getFirst();
        if (headers.size() != 1 || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        TokenClaims claims;
        try {
            claims = tokenProvider.parseAccessToken(authorization.substring(7).trim());
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (claims == null || claims.userId() == null || claims.userId() <= 0 || claims.role() == null
                    || claims.sessionId() == null || claims.sessionId().isBlank() || claims.expiresAt() == null
                    || !claims.expiresAt().isAfter(now)
                    || !refreshTokenReader.isActiveSession(claims.userId(), claims.sessionId(), now)) {
                reject(response, ErrorCode.UNAUTHORIZED);
                return;
            }
        } catch (InvalidTokenException invalid) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return;
        } catch (SessionStoreUnavailableException | DataAccessException | org.springframework.transaction.TransactionException unavailable) {
            reject(response, ErrorCode.AUTH_SESSION_STORE_UNAVAILABLE);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(claims.userId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void reject(HttpServletResponse response, ErrorCode error) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (error == ErrorCode.AUTH_SESSION_STORE_UNAVAILABLE) response.setHeader("Retry-After", "1");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error.getMessage(), error.name()));
    }
}
