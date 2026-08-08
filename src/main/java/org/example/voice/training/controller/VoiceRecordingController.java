package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.application.VoiceRecordingService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class VoiceRecordingController {

    private final VoiceRecordingService voiceRecordingService;
}
