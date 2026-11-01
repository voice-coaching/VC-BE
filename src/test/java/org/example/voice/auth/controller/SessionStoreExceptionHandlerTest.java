package org.example.voice.auth.controller;

import org.example.voice.auth.application.*;
import org.example.voice.auth.exception.SessionStoreUnavailableException;
import org.example.voice.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionStoreExceptionHandlerTest {
    @Test void unavailableSessionChangeIs503WithRetryHeadersAndNoCredentials() throws Exception {
        var auth=mock(AuthService.class);
        when(auth.login(any(),any())).thenThrow(new SessionStoreUnavailableException());
        var controller=new AuthController(auth,mock(SocialLoginService.class),mock(TokenService.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new SessionStoreExceptionHandler(),new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"fixture@example.invalid\",\"password\":\"Fixture123!\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Retry-After","1"))
                .andExpect(jsonPath("$.result").value(false)).andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"));
    }
}
