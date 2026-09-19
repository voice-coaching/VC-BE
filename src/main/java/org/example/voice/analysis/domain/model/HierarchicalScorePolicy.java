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
            if (input == null) throw new IllegalStateException("missing hierarchy rubric");
            var bytes = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            POLICY = new ObjectMapper().readTree(bytes);
            SHA256 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private HierarchicalScorePolicy() {}
    public record VisualObservation(String status, Integer selectedExpectedIndex, Integer videoStartMs,
            Integer videoEndMs, String supplementSha256, Map<String, BigDecimal> measurements) {}

    static void validate(ClovaScoreEvidence evidence) {
        var leaves = evidence.phoneCriteria();
        var rules = POLICY.get("phoneCriteria");
        if (leaves == null || leaves.size() != rules.size()) invalid();
        Map<String, int[]> totals = new HashMap<>();
        Set<String> eligible = new HashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            var row = leaves.get(i);
            var rule = rules.get(i);
            if (row == null || !rule.get("id").asText().equals(row.criterionId())
                    || row.sampleCount() < 0 || row.sampleCount() > evidence.expectedPhoneCount()) invalid();
            int total = 0;
            int[] counts = row.counts();
            for (int n : counts) { if (n < 0 || n > row.sampleCount()) invalid(); total += n; }
            if (total != row.sampleCount() || !Objects.equals(level(row), row.level())) invalid();
            int[] parent = totals.computeIfAbsent(rule.get("parentId").asText(), key -> new int[6]);
            for (int j = 0; j < 6; j++) parent[j] += counts[j];
            if (row.level() != null && row.level() < 4) eligible.add(row.criterionId());
        }
        for (var parent : evidence.criteria()) {
            if (!"coverage".equals(parent.criterionId())
                    && !Arrays.equals(parent.counts(), totals.get(parent.criterionId()))) invalid();
        }
        var attention = evidence.attentionCriterionIds();
        if (attention == null || attention.size() > 3 || new HashSet<>(attention).size() != attention.size()
                || !eligible.containsAll(attention) || (!eligible.isEmpty() && attention.isEmpty())) invalid();
        validateVisual(evidence.visualObservation(), evidence.expectedPhoneCount());
    }

    private static Integer level(ClovaScoreEvidence.Criterion row) {
        if (row.sampleCount() == 0) return null;
        int units = 4 * row.matchedClear() + 2 * row.matchedFlagged() + 3 * row.substitutedClear() + row.substitutedFlagged();
        for (var band : POLICY.get("levels")) {
            if (units * 100 >= band.get("minimumPercent").asInt() * 4 * row.sampleCount()) return band.get("level").asInt();
        }
        throw new IllegalArgumentException("invalid hierarchy level");
    }

    private static void validateVisual(VisualObservation value, int count) {
        if (value == null || value.status() == null || value.measurements() == null
                || !MEASUREMENTS.containsAll(value.measurements().keySet())) invalid();
        if ("NOT_PROVIDED".equals(value.status()) || "UNAVAILABLE".equals(value.status())) {
            if (!value.measurements().isEmpty() || value.selectedExpectedIndex() != null || value.videoStartMs() != null
                    || value.videoEndMs() != null || value.supplementSha256() != null) invalid();
            return;
        }
        if (!"NOT_VALIDATED".equals(value.status()) || value.measurements().isEmpty()
                || value.selectedExpectedIndex() == null || value.selectedExpectedIndex() < 0 || value.selectedExpectedIndex() >= count
                || value.videoStartMs() == null || value.videoEndMs() == null || value.videoStartMs() < 0
                || value.videoEndMs() <= value.videoStartMs() || value.videoEndMs() > 180000
                || value.supplementSha256() == null || !value.supplementSha256().matches("[0-9a-f]{64}")) invalid();
        for (var measurement : value.measurements().values()) {
            if (measurement == null || measurement.signum() < 0 || measurement.compareTo(BigDecimal.valueOf(1000000)) > 0) invalid();
        }
    }

    /** Tie optional observations to the same approved callback anchor and supplement. */
    public static void validateBinding(ClovaScoreEvidence evidence, AnalysisWorkerResult result) {
        if (!ClovaScoreEvidence.HIERARCHICAL_RUBRIC.equals(evidence.rubricRevision())) return;
        var observation = evidence.visualObservation();
        if (!"NOT_VALIDATED".equals(observation.status())) return;
        var visual = result.visualSupplement();
        var audio = result.pronunciationEvidence();
        if (visual == null || audio == null
                || !Objects.equals(observation.selectedExpectedIndex(), visual.selectedExpectedIndex())
                || !Objects.equals(observation.selectedExpectedIndex(), audio.selectedExpectedIndex())
                || !Objects.equals(observation.supplementSha256(), visual.supplementSha256())) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("invalid hierarchy evidence"); }
}
