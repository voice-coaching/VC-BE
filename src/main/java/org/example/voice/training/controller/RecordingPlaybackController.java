package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.training.application.VoiceRecordingService;
import org.example.voice.training.controller.dto.RecordingPlaybackUrlResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recordings")
public class RecordingPlaybackController {

    private final VoiceRecordingService voiceRecordingService;

    @GetMapping("/{recordingId}/playback-url")
    public ApiResponse<RecordingPlaybackUrlResponseDto> getPlaybackUrl(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long recordingId
    ) {
        return ApiResponse.success(
                "녹음 재생 URL을 발급했습니다.",
                RecordingPlaybackUrlResponseDto.from(voiceRecordingService.getPlaybackUrl(recordingId, user.id()))
        );
    }
}
