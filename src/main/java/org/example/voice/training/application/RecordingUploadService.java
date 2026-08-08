package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.infrastructure.PresignedUrlProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordingUploadService {

    private final PresignedUrlProvider presignedUrlProvider;
}
