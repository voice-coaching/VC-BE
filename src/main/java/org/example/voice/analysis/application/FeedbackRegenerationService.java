package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackRegenerationService {

    private final AiFeedbackProvider aiFeedbackProvider;
}
