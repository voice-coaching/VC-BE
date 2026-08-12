package org.example.voice.auth.controller;

import org.example.voice.auth.application.AuthService;
import org.example.voice.auth.application.SocialLoginService;
import org.example.voice.auth.application.TokenService;
import org.example.voice.auth.domain.model.AuthSession;
import org.example.voice.common.exception.GlobalExceptionHandler;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {
    private AuthService authService;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        tokenService = mock(TokenService.class);
        AuthController controller = new AuthController(authService, mock(SocialLoginService.class), tokenService);
        ReflectionTestUtils.setField(controller, "refreshTokenSeconds", 1200L);
        ReflectionTestUtils.setField(controller, "secureCookie", true);
        ReflectionTestUtils.setField(controller, "sameSite", "None");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void emailAvailabilityReturnsDuplicateAsNormalResponse() throws Exception {
        when(authService.isEmailAvailable("used@example.com")).thenReturn(false);
        mockMvc.perform(get("/api/auth/email-availability").param("email", "used@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void signupReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L); when(user.getEmail()).thenReturn("user@example.com"); when(user.getNickname()).thenReturn("nick");
        when(authService.signup("user@example.com", "Password123!", "nick", true, true))
                .thenReturn(new AuthSession(user, "access", "refresh", 600, true, false));

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"user@example.com","password":"Password123!","nickname":"nick","termsAgreed":true,"privacyAgreed":true}
                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refreshToken=refresh"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("SameSite=None"))))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true));
    }

    @Test
    void missingRefreshCookieUsesSpecifiedErrorContract() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token입니다."));
    }
}
