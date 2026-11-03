package org.example.voice.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.port.TokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Compatibility identity adapter. Both the header and token subject are unverified caller input. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenProvider tokenProvider;
    private final org.example.voice.auth.domain.port.RefreshTokenReader refreshTokenReader;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = null;
        String selected = request.getHeader("X-User-Id");
        String authorization = request.getHeader("Authorization");
        try {
            if (selected != null && !selected.isBlank()) userId = Long.valueOf(selected);
            else if (authorization != null && authorization.startsWith("Bearer "))
                userId = tokenProvider.parseAccessToken(authorization.substring(7)).userId();
        } catch (RuntimeException ignored) {
            // An unreadable selector supplies no user; it never rejects the request.
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, List.of()));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
