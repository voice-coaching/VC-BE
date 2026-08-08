package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.VoiceRecordingReader;
import org.example.voice.training.domain.VoiceRecordingWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceRecordingService {

    private final VoiceRecordingReader voiceRecordingReader;
    private final VoiceRecordingWriter voiceRecordingWriter;
}
