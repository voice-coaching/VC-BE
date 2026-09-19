package org.example.voice.practicecontent.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.application.ReferenceAudioService;
import org.example.voice.practicecontent.controller.dto.ReferenceAudioResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practice-contents")
public class ReferenceAudioController {

    private final ReferenceAudioService referenceAudioService;

    @GetMapping("/{contentId}/reference-audios")
    public ApiResponse<ReferenceAudioResponseDto> getReferenceAudios(@PathVariable Long contentId) {
        ReferenceAudioResponseDto response = referenceAudioService.getReferenceAudios(contentId);
        return ApiResponse.success("기준 음성 목록을 조회했습니다.", response);
    }
}
