package org.example.voice.practiceexample.domain.port;

import java.util.Optional;
import org.example.voice.practiceexample.domain.model.ExampleData.Audio;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.Generated;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.Job;

public interface ExampleTtsStore {
    void reconcile(String revision, String fingerprint);
    Optional<Job> claim(String revision);
    default Optional<Generated> cached(Job job) { return Optional.empty(); }
    default boolean stage(Job job, Generated audio) { return true; }
    boolean complete(Job job, Generated audio);
    void fail(Job job, String code, boolean retryable, long delaySeconds);
    Optional<Audio> playable(String exampleId, String revision);
}
