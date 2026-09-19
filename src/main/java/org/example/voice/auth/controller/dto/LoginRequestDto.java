package org.example.voice.auth.controller.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record LoginRequestDto(@NotBlank @Email String email, @NotBlank String password) {}
