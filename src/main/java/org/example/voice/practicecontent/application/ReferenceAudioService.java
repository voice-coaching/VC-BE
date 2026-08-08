package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.port.ReferenceAudioReader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReferenceAudioService {

    private final ReferenceAudioReader referenceAudioReader;
}
