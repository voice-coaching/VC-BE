package org.example.voice.practiceexample.domain.model;

import org.example.voice.practicecontent.domain.type.LearningFocus;
import java.util.List;

public final class ExampleData {
    private ExampleData() {}
    public record Item(String id, int order, String text, String hint, LearningFocus focus, String locale, Long practiceContentId) {}
    public record Examples(Long courseId, Long stepId, int revision, List<Item> items) {}
    public record Snapshot(String id, Long setId, int revision, Long stepId, Long educationRevisionId, String text) {}
    public record Audio(byte[] bytes, String etag) {}
    public record Claim(Audio cached, String lease) {}
}
