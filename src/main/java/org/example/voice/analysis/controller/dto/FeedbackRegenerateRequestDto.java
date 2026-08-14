package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.type.FeedbackStyle;

public record FeedbackRegenerateRequestDto(@NotNull FeedbackStyle feedbackStyle) {}
