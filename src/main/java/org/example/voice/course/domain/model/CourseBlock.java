package org.example.voice.course.domain.model;

import java.net.URI;
import java.util.List;
import java.util.Set;

/** Plain text and explicitly supported structured content only. Never interpreted as HTML. */
public record CourseBlock(String type, String title, String body, String assetUrl, String altText,
                          Double aspectRatio, Diagram diagram, List<String> items,
                          Long referenceAudioId, Long practiceContentId) {
    public record Diagram(String kind, String altText) {}
    private static final Set<String> DIAGRAMS = Set.of("TONGUE_POSITION_RIEUL");

    public CourseBlock {

        if (title != null) text(title);
        switch (type) {
            case "TEXT" -> text(body);
            case "IMAGE" -> { safeUrl(assetUrl); text(altText);  }
            case "DIAGRAM" -> {  text(diagram.altText()); }
            case "CHECKLIST" -> {  items.forEach(CourseBlock::text); items = List.copyOf(items); }
            case "AUDIO" -> {  }
            case "PRACTICE_PROMPT" -> {  }
            default -> {}
        }
        // Do not forward unrelated fields that could bypass the selected type's validation.

    }
    private static void text(String value) {

    }
    private static void safeUrl(String value) {
        try {
            URI uri = URI.create(value);

        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid asset URL"); }
    }

}
