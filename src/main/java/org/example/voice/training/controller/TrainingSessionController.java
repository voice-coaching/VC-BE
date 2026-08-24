package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.training.controller.dto.TrainingSessionCancelResponseDto;
import org.example.voice.training.controller.dto.TrainingSessionCompleteRequestDto;
import org.example.voice.training.controller.dto.TrainingSessionCompleteResponseDto;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.controller.dto.TrainingSessionDetailResponseDto;
import org.example.voice.training.controller.dto.TrainingSessionResponseDto;
import org.example.voice.training.application.TrainingSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class TrainingSessionController {

    private final TrainingSessionService trainingSessionService;

    @PostMapping
    public ApiResponse<TrainingSessionResponseDto> createTrainingSession(
            @AuthenticationPrincipal LoginUser user,
            @RequestBody TrainingSessionCreateRequestDto request
    ) {
        return ApiResponse.success(
                "학습 세션이 생성되었습니다.",
                TrainingSessionResponseDto.from(trainingSessionService.create(request, user.id()))
        );
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<TrainingSessionDetailResponseDto> getTrainingSession(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "학습 세션을 조회했습니다.",
                TrainingSessionDetailResponseDto.from(trainingSessionService.getSession(sessionId, user.id()))
        );
    }

    @PostMapping("/{sessionId}/complete")
    public ApiResponse<TrainingSessionCompleteResponseDto> completeTrainingSession(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @RequestBody TrainingSessionCompleteRequestDto request
    ) {
        return ApiResponse.success(
                "학습이 완료되었습니다.",
                TrainingSessionCompleteResponseDto.from(
                        trainingSessionService.complete(sessionId, request.totalLearningSeconds(), user.id())
                )
        );
    }

    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<TrainingSessionCancelResponseDto> cancelTrainingSession(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "학습이 취소되었습니다.",
                TrainingSessionCancelResponseDto.from(trainingSessionService.cancel(sessionId, user.id()))
        );
    }
}
