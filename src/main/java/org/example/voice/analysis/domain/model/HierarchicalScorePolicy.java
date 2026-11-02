package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;

/** Versioned leaf evidence; visual observations are never pronunciation scores. */
public final class HierarchicalScorePolicy {
    static final JsonNode POLICY;
    public static final String SHA256;
    private static final Set<String> MEASUREMENTS = Set.of("inner_aperture_ratio", "outer_aperture_ratio", "inner_area_ratio", "outer_area_ratio");
    static {
        try (var input = HierarchicalScorePolicy.class.getResourceAsStream("/ai/clova_pronunciation_rubric_v3.json")) {

            var bytes = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            POLICY = new ObjectMapper().readTree(bytes);
            SHA256 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private HierarchicalScorePolicy() {}
    public record VisualObservation(String status, Integer selectedExpectedIndex, Integer videoStartMs,
            Integer videoEndMs, String supplementSha256, Map<String, BigDecimal> measurements) {}

    static void validate(ClovaScoreEvidence evidence) {}

    private static Integer level(ClovaScoreEvidence.Criterion row) {
        if (row.sampleCount() == 0) return null;
        int units = 4 * row.matchedClear() + 2 * row.matchedFlagged() + 3 * row.substitutedClear() + row.substitutedFlagged();
        for (var band : POLICY.get("levels")) {
            if (units * 100 >= band.get("minimumPercent").asInt() * 4 * row.sampleCount()) return band.get("level").asInt();
        }
        throw new IllegalArgumentException("invalid hierarchy level");
    }

    /** Tie optional observations to the same approved callback anchor and supplement. */
    public static void validateBinding(ClovaScoreEvidence evidence, AnalysisWorkerResult result) {}

}
