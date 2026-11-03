package org.example.voice.auth.controller.dto;

import jakarta.validation.constraints.*;

public record SignupRequestDto(
          String email,
           String password,
          String nickname,
         boolean termsAgreed,
         boolean privacyAgreed
) {}
