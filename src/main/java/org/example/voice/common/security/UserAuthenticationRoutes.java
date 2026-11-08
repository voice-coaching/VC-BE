package org.example.voice.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/** Routes with public access or a dedicated service authentication boundary. */
public final class UserAuthenticationRoutes {
    private UserAuthenticationRoutes() {}

    public static final List<String> PUBLIC_GET = List.of(
            "/api/auth/email-availability", "/api/browser-video/.well-known/upload-grant-keys",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/internal/actuator/health/**", "/internal/actuator/prometheus");
    public static final List<String> PUBLIC_POST = List.of(
            "/api/auth/signup", "/api/auth/login", "/api/auth/social-login", "/api/auth/token/refresh",
            "/api/payments/webhooks/portone");
    public static final String INTERNAL_AI = "/api/internal/ai/**";

    public static boolean exempt(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "OPTIONS".equals(request.getMethod()) || matches(path, INTERNAL_AI)
                || "GET".equals(request.getMethod()) && PUBLIC_GET.stream().anyMatch(pattern -> matches(path, pattern))
                || "POST".equals(request.getMethod()) && PUBLIC_POST.contains(path);
    }

    private static boolean matches(String path, String pattern) {
        if (!pattern.endsWith("/**")) return pattern.equals(path);
        String root = pattern.substring(0, pattern.length() - 3);
        return path.equals(root) || path.startsWith(root + '/');
    }
}
