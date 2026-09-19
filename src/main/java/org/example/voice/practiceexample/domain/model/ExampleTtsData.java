package org.example.voice.practiceexample.domain.model;

import java.util.UUID;

public final class ExampleTtsData {
    private ExampleTtsData() {}
    public record Job(long id, String exampleId, String text, int revision, String profileRevision, UUID owner, int attempt) {}
    public record Generated(byte[] bytes, String sha256, int durationMs) {}
}
