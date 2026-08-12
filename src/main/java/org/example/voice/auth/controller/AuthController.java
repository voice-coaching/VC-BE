package org.example.voice.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.application.AuthService;
import org.example.voice.auth.application.SocialLoginService;
import org.example.voice.auth.application.TokenService;
import org.example.voice.auth.controller.dto.*;
import org.example.voice.auth.domain.model.AuthSession;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SocialLoginService socialLoginService;
    private final TokenService tokenService;

    @Value("${auth.jwt.refresh-token-seconds}") private long refreshTokenSeconds;
    @Value("${auth.cookie.secure}") private boolean secureCookie;
    @Value("${auth.cookie.same-site}") private String sameSite;

    @GetMapping("/email-availability")
    public ApiResponse<EmailAvailabilityResponseDto> emailAvailability(@RequestParam @NotBlank @Email String email) {
        boolean available = authService.isEmailAvailable(email);
        String message = available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다.";
        return ApiResponse.success(message, new EmailAvailabilityResponseDto(email, available));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponseDto>> signup(@Valid @RequestBody SignupRequestDto request) {
        AuthSession session = authService.signup(request.email(), request.password(), request.nickname(), request.termsAgreed(), request.privacyAgreed());
        return withCookie(ApiResponse.success("회원가입이 완료되었습니다.", SignupResponseDto.from(session)), session.refreshToken());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
        AuthSession session = authService.login(request.email(), request.password());
        return withCookie(ApiResponse.success("로그인되었습니다.", LoginResponseDto.from(session)), session.refreshToken());
    }

    @PostMapping("/social-login")
    public ResponseEntity<ApiResponse<SocialLoginResponseDto>> socialLogin(@Valid @RequestBody SocialLoginRequestDto request) {
        AuthSession session = socialLoginService.login(request.provider(), request.authorizationCode(), request.redirectUri());
        return withCookie(ApiResponse.success("SNS 로그인이 완료되었습니다.", SocialLoginResponseDto.from(session)), session.refreshToken());
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponseDto>> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) throw new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN);
        IssuedTokens tokens = tokenService.rotate(refreshToken);
        TokenRefreshResponseDto response = new TokenRefreshResponseDto(tokens.accessToken(), "Bearer", tokenService.accessTokenSeconds());
        return withCookie(ApiResponse.success("토큰을 갱신하였습니다.", response), tokens.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal LoginUser loginUser,
                                                     @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        tokenService.revoke(loginUser.id(), refreshToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .body(ApiResponse.success("로그아웃하였습니다."));
    }

    private <T> ResponseEntity<ApiResponse<T>> withCookie(ApiResponse<T> response, String refreshToken) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(refreshToken).toString()).body(response);
    }

    private ResponseCookie cookie(String value) {
        return ResponseCookie.from("refreshToken", value).httpOnly(true).secure(secureCookie).sameSite(sameSite)
                .path("/api/auth").maxAge(Duration.ofSeconds(refreshTokenSeconds)).build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from("refreshToken", "").httpOnly(true).secure(secureCookie).sameSite(sameSite)
                .path("/api/auth").maxAge(Duration.ZERO).build();
    }
}
