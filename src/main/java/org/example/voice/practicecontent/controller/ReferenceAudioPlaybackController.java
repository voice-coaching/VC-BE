package org.example.voice.practicecontent.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.application.ReferenceAudioService;
import org.example.voice.practicecontent.controller.dto.ReferenceAudioPlaybackUrlResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reference-audios")
public class ReferenceAudioPlaybackController {

    private final ReferenceAudioService referenceAudioService;

    @GetMapping("/{audioId}/playback-url")
    public ApiResponse<ReferenceAudioPlaybackUrlResponseDto> getPlaybackUrl(@PathVariable Long audioId) {
        ReferenceAudioPlaybackUrlResponseDto response = referenceAudioService.getPlaybackUrl(audioId);
        return ApiResponse.success("기준 음성 재생 URL을 발급했습니다.", response);
    }
}
