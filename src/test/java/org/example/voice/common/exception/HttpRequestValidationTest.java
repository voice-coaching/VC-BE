package org.example.voice.common.exception;

import org.example.voice.auth.application.AuthService;
import org.example.voice.auth.application.SocialLoginService;
import org.example.voice.auth.application.TokenService;
import org.example.voice.auth.controller.AuthController;
import org.example.voice.mypage.application.MyPageService;
import org.example.voice.mypage.controller.MyPageController;
import org.example.voice.profileimage.application.ProfileImageService;
import org.example.voice.profileimage.controller.ProfileImageController;
import org.example.voice.profileimage.controller.ProfileImageExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HttpRequestValidationTest {
    private AuthService auth;
    private MyPageService myPage;
    private ProfileImageService images;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        myPage = mock(MyPageService.class);
        images = mock(ProfileImageService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(auth, mock(SocialLoginService.class), mock(TokenService.class)),
                        new MyPageController(myPage), new ProfileImageController(images))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ProfileImageExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/email-availability", "/api/users/me/score-trends"})
    void missingRequiredQueryReturnsValidationErrorWithoutCallingServices(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(auth, myPage, images);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void jsonPhotoUploadReturnsUnsupportedMediaTypeWithoutCallingStorage(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/users/me/profile-image")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept", MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 Content-Type입니다."))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(auth, myPage, images);
    }

    @Test
    void unexpectedServiceFailureStillReturnsInternalServerErrorWithoutLeakingDetails() throws Exception {
        when(auth.isEmailAvailable("test@example.invalid"))
                .thenThrow(new IllegalStateException("internal diagnostic must not be returned"));
        mvc.perform(get("/api/auth/email-availability").param("email", "test@example.invalid"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
