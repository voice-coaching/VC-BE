package org.example.voice.analysis.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** An auditable product rubric, not a calibrated pronunciation probability. */
public record ClovaScoreEvidence(String rubricRevision, String generator, String modelRevision,
        String evidenceSha256, int expectedPhoneCount, int alignedPhoneCount,
        int correctPhoneCount, int unflaggedPhoneCount,
        BigDecimal alignmentScore, BigDecimal coverageScore, BigDecimal stabilityScore,
        String rubricSha256, String promptSha256, java.util.List<Criterion> criteria, java.util.List<Criterion> phoneCriteria,
        HierarchicalScorePolicy.VisualObservation visualObservation, java.util.List<String> attentionCriterionIds) {
    public ClovaScoreEvidence(String rubricRevision, String generator, String modelRevision,
            String evidenceSha256, int expectedPhoneCount, int alignedPhoneCount,
            int correctPhoneCount, int unflaggedPhoneCount, BigDecimal alignmentScore,
            BigDecimal coverageScore, BigDecimal stabilityScore, String rubricSha256,
            String promptSha256, java.util.List<Criterion> criteria) {
        this(rubricRevision, generator, modelRevision, evidenceSha256, expectedPhoneCount,
                alignedPhoneCount, correctPhoneCount, unflaggedPhoneCount, alignmentScore,
                coverageScore, stabilityScore, rubricSha256, promptSha256, criteria, null, null, null);
    }
    public static final String HIERARCHICAL_RUBRIC = "clova-phone-rubric-v3";
    public static final String RUBRIC = "clova-phone-rubric-v1";
    public static final String DETAILED_RUBRIC = "clova-phone-rubric-v2";
    public void validate(BigDecimal overallScore) {}
    public record Criterion(String criterionId, int sampleCount, int matchedClear, int matchedFlagged,
            int substitutedClear, int substitutedFlagged, int deletedClear, int deletedFlagged,
            Integer level) {
        int[] counts() { return new int[]{matchedClear, matchedFlagged, substitutedClear,
                substitutedFlagged, deletedClear, deletedFlagged}; }
    }
    private static final com.fasterxml.jackson.databind.JsonNode POLICY;
    public static final String POLICY_SHA256;
    static {
        try (var input = ClovaScoreEvidence.class.getResourceAsStream("/ai/clova_pronunciation_rubric_v2.json")) {

            byte[] bytes = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            POLICY = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            POLICY_SHA256 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private BigDecimal component(int count, int weight) {
        return BigDecimal.valueOf((long) count * weight)
                .divide(BigDecimal.valueOf(expectedPhoneCount), 1, RoundingMode.HALF_UP);
    }

    /** Public projection uses the same pinned rules as callback validation. */
    public AnalysisScoreBreakdown breakdown(BigDecimal overallScore) {

        if (!DETAILED_RUBRIC.equals(rubricRevision) && !HIERARCHICAL_RUBRIC.equals(rubricRevision)) return null;
        var items = new java.util.ArrayList<AnalysisScoreBreakdown.Item>();
        int applicableMaxScore = 0;
        for (int i = 0; i < criteria.size(); i++) {
            var criterion = criteria.get(i);
            var rule = POLICY.get("criteria").get(i);
            int weight = rule.get("weight").asInt();
            boolean applicable = criterion.sampleCount() > 0;
            var phones = new java.util.ArrayList<String>();
            rule.get("phones").forEach(phone -> phones.add(phone.asText()));
            BigDecimal score = applicable
                    ? BigDecimal.valueOf((long) weight * criterion.level()).divide(BigDecimal.valueOf(4))
                    : null;
            if (applicable) applicableMaxScore += weight;
            items.add(new AnalysisScoreBreakdown.Item(criterion.criterionId(), rule.get("label").asText(),
                    AnalysisScoreBreakdown.description(criterion.criterionId()), java.util.List.copyOf(phones),
                    weight, applicable, criterion.sampleCount(), criterion.level(), score));
        }
        return new AnalysisScoreBreakdown(rubricRevision, applicableMaxScore, java.util.List.copyOf(items));
    }

    public Map<String, Object> audit() {
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("rubricRevision", rubricRevision); values.put("generator", generator);
        values.put("modelRevision", modelRevision); values.put("evidenceSha256", evidenceSha256);
        values.put("expectedPhoneCount", expectedPhoneCount); values.put("alignedPhoneCount", alignedPhoneCount);
        values.put("correctPhoneCount", correctPhoneCount); values.put("unflaggedPhoneCount", unflaggedPhoneCount);
        if (DETAILED_RUBRIC.equals(rubricRevision) || HIERARCHICAL_RUBRIC.equals(rubricRevision)) {
            values.put("rubricSha256", rubricSha256); values.put("promptSha256", promptSha256);
            values.put("criteria", criteria);
        } else {
            values.put("alignmentScore", alignmentScore); values.put("coverageScore", coverageScore);
            values.put("stabilityScore", stabilityScore);
        }
        if (HIERARCHICAL_RUBRIC.equals(rubricRevision)) {
            values.put("phoneCriteria", phoneCriteria);
            values.put("visualObservation", visualObservation);
            values.put("attentionCriterionIds", attentionCriterionIds);
        }
        return values;
    }
}
