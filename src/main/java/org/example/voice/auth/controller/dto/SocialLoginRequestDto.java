package org.example.voice.auth.controller.dto;
import jakarta.validation.constraints.NotBlank;
public record SocialLoginRequestDto(
         String provider,
         String authorizationCode,
         String redirectUri,
        String state
) {}
