package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.training.application.RecordingUploadService;
import org.example.voice.training.application.VoiceRecordingService;
import org.example.voice.training.controller.dto.RecordingRegisterRequestDto;
import org.example.voice.training.controller.dto.RecordingSelectionResponseDto;
import org.example.voice.training.controller.dto.RecordingUploadUrlRequestDto;
import org.example.voice.training.controller.dto.RecordingUploadUrlResponseDto;
import org.example.voice.training.controller.dto.VoiceRecordingListResponseDto;
import org.example.voice.training.controller.dto.VoiceRecordingResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class VoiceRecordingController {

    private final VoiceRecordingService voiceRecordingService;
    private final RecordingUploadService recordingUploadService;

    @PostMapping("/{sessionId}/recordings/upload-url")
    public ApiResponse<RecordingUploadUrlResponseDto> createUploadUrl(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @RequestBody RecordingUploadUrlRequestDto request
    ) {
        return ApiResponse.success(
                "녹음 업로드 URL을 발급했습니다.",
                RecordingUploadUrlResponseDto.from(recordingUploadService.createUploadUrl(sessionId, request, user.id()))
        );
    }

    @PostMapping("/{sessionId}/recordings")
    public ApiResponse<VoiceRecordingResponseDto> registerRecording(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @RequestBody RecordingRegisterRequestDto request
    ) {
        return ApiResponse.success(
                "녹음 파일이 등록되었습니다.",
                VoiceRecordingResponseDto.from(voiceRecordingService.register(sessionId, request, user.id()))
        );
    }

    @GetMapping("/{sessionId}/recordings")
    public ApiResponse<VoiceRecordingListResponseDto> getRecordings(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "녹음 시도 목록을 조회했습니다.",
                VoiceRecordingListResponseDto.from(voiceRecordingService.getRecordings(sessionId, user.id()))
        );
    }

    @PatchMapping("/{sessionId}/recordings/{recordingId}/select")
    public ApiResponse<RecordingSelectionResponseDto> selectRecording(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @PathVariable Long recordingId
    ) {
        return ApiResponse.success(
                "최종 녹음이 선택되었습니다.",
                RecordingSelectionResponseDto.from(voiceRecordingService.select(sessionId, recordingId, user.id()))
        );
    }

    @DeleteMapping("/{sessionId}/recordings/{recordingId}")
    public ApiResponse<Void> deleteRecording(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @PathVariable Long recordingId
    ) {
        voiceRecordingService.delete(sessionId, recordingId, user.id());
        return ApiResponse.success("녹음 파일이 삭제되었습니다.");
    }
}
