package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.port.PracticeContentReader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeContentService {

    private final PracticeContentReader practiceContentReader;
}
