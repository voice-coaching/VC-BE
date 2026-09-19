package org.example.voice.practiceexample.domain.port;

import org.example.voice.practiceexample.domain.model.ExampleTtsData.Generated;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.Job;

public interface ExampleTtsGenerator {
    Generated generate(Job job);
}
