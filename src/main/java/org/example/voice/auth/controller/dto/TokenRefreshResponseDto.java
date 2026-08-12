package org.example.voice.auth.controller.dto;
public record TokenRefreshResponseDto(String accessToken, String tokenType, long expiresIn) {}
