package org.example.voice.practiceexample.domain.port;

import java.util.Optional;
import org.example.voice.practiceexample.domain.model.ExampleData.Audio;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.*;

public interface ExampleTtsStore {
    void reconcile(String revision, String fingerprint);
    Optional<Job> claim(String revision);
    boolean complete(Job job, Generated audio);
    void fail(Job job, String code, boolean retryable, long delaySeconds);
    Optional<Audio> approved(String exampleId, String revision);
}
