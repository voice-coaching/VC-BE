package org.example.voice.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.AdminAuthenticationService;
import org.example.voice.common.security.LoginUser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import java.util.List;

/** Keeps administrator authentication local to this module; does not change global security policy. */
@RestControllerAdvice(basePackageClasses = AdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminAuthenticationAdvice {
    private final AdminAuthenticationService authentication;

    @ModelAttribute
    public void authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim() : null;
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        boolean adminRequired = !(handler instanceof HandlerMethod method
                && UserPopupController.class.isAssignableFrom(method.getBeanType()));
        // Never inherit identity from X-User-Id or another unverified request selector.
        SecurityContextHolder.clearContext();
        var claims = authentication.authenticate(token, adminRequired);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(claims.userId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))));
    }
}
