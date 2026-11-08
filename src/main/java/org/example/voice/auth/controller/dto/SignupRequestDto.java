package org.example.voice.auth.controller.dto;

import jakarta.validation.constraints.*;

public record SignupRequestDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$") String password,
        @NotBlank @Size(max = 30) String nickname,
        @AssertTrue boolean termsAgreed,
        @AssertTrue boolean privacyAgreed
) {}
