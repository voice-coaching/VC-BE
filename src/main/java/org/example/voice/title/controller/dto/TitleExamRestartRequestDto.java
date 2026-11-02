package org.example.voice.title.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TitleExamRestartRequestDto(  Long previousTrainingSessionId) {}
