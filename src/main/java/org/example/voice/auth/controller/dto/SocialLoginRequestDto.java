package org.example.voice.auth.controller.dto;
import jakarta.validation.constraints.NotBlank;
public record SocialLoginRequestDto(@NotBlank String provider, @NotBlank String authorizationCode, @NotBlank String redirectUri) {}
