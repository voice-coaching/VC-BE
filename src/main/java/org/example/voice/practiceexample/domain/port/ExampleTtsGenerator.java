package org.example.voice.practiceexample.domain.port;

import org.example.voice.practiceexample.domain.model.ExampleTtsData.*;

public interface ExampleTtsGenerator {
    Generated generate(Job job);
}
