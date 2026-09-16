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
        if (type == null) throw new IllegalArgumentException("Missing block type");
        if (title != null) text(title);
        switch (type) {
            case "TEXT" -> text(body);
            case "IMAGE" -> { safeUrl(assetUrl); text(altText); if (aspectRatio == null || !Double.isFinite(aspectRatio) || aspectRatio <= 0) invalid(); }
            case "DIAGRAM" -> { if (diagram == null || !DIAGRAMS.contains(diagram.kind())) invalid(); text(diagram.altText()); }
            case "CHECKLIST" -> { if (items == null || items.isEmpty() || items.size() > 50) invalid(); items.forEach(CourseBlock::text); items = List.copyOf(items); }
            case "AUDIO" -> { if (referenceAudioId == null || referenceAudioId <= 0) invalid(); }
            case "PRACTICE_PROMPT" -> { if (practiceContentId == null || practiceContentId <= 0) invalid(); }
            default -> invalid();
        }
        // Do not forward unrelated fields that could bypass the selected type's validation.
        if ((!type.equals("TEXT") && (title != null || body != null))
                || (!type.equals("IMAGE") && (assetUrl != null || altText != null || aspectRatio != null))
                || (!type.equals("DIAGRAM") && diagram != null)
                || (!type.equals("CHECKLIST") && items != null)
                || (!type.equals("AUDIO") && referenceAudioId != null)
                || (!type.equals("PRACTICE_PROMPT") && practiceContentId != null)) invalid();
    }
    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 20000 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) invalid();
    }
    private static void safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) invalid();
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid asset URL"); }
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid education block"); }
}
