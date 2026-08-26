package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.controller.dto.ReferenceAudioPlaybackUrlResponseDto;
import org.example.voice.practicecontent.controller.dto.ReferenceAudioResponseDto;
import org.example.voice.practicecontent.domain.port.ReferenceAudioReader;
import org.example.voice.practicecontent.exception.ContentNotFoundException;
import org.example.voice.practicecontent.exception.ReferenceAudioNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceAudioService {

    private final ReferenceAudioReader referenceAudioReader;

    @Transactional(readOnly = true)
    public ReferenceAudioResponseDto getReferenceAudios(Long contentId) {
        if (!referenceAudioReader.existsPracticeContent(contentId)) {
            throw new ContentNotFoundException();
        }
        return ReferenceAudioResponseDto.from(referenceAudioReader.findReferenceAudiosByContentId(contentId).items());
    }

    @Transactional(readOnly = true)
    public ReferenceAudioPlaybackUrlResponseDto getPlaybackUrl(Long audioId) {
        return referenceAudioReader.findPlaybackUrl(audioId)
                .map(ReferenceAudioPlaybackUrlResponseDto::from)
                .orElseThrow(ReferenceAudioNotFoundException::new);
    }
}
