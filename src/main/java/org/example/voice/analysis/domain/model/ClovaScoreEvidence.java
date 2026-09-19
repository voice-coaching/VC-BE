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
    public void validate(BigDecimal overallScore) {
        if (!HIERARCHICAL_RUBRIC.equals(rubricRevision)
                && (phoneCriteria != null || visualObservation != null || attentionCriterionIds != null)) invalid();
        if (DETAILED_RUBRIC.equals(rubricRevision) || HIERARCHICAL_RUBRIC.equals(rubricRevision)) {
            validateDetailed(overallScore);
            if (HIERARCHICAL_RUBRIC.equals(rubricRevision)) HierarchicalScorePolicy.validate(this);
            return;
        }
        if (!RUBRIC.equals(rubricRevision) || !"hyperclova".equals(generator)
                || modelRevision == null || !modelRevision.matches("[0-9a-f]{40}")
                || evidenceSha256 == null || !evidenceSha256.matches("[0-9a-f]{64}")
                || expectedPhoneCount < 1 || expectedPhoneCount > 4096
                || correctPhoneCount < 0 || correctPhoneCount > alignedPhoneCount
                || alignedPhoneCount > expectedPhoneCount || unflaggedPhoneCount < 0
                || unflaggedPhoneCount > expectedPhoneCount || overallScore == null
                || alignmentScore == null || coverageScore == null || stabilityScore == null
                || alignmentScore.compareTo(component(correctPhoneCount, 60)) != 0
                || coverageScore.compareTo(component(alignedPhoneCount, 20)) != 0
                || stabilityScore.compareTo(component(unflaggedPhoneCount, 20)) != 0
                || rubricSha256 != null || promptSha256 != null || criteria != null
                || overallScore.compareTo(alignmentScore.add(coverageScore).add(stabilityScore)) != 0) {
            throw new IllegalArgumentException("invalid CLOVA rubric score");
        }
    }
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
            if (input == null) throw new IllegalStateException("missing CLOVA rubric");
            byte[] bytes = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            POLICY = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            POLICY_SHA256 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private void validateDetailed(BigDecimal overallScore) {
        if (!"hyperclova".equals(generator) || modelRevision == null || !modelRevision.matches("[0-9a-f]{40}")
                || evidenceSha256 == null || !evidenceSha256.matches("[0-9a-f]{64}")
                || !(HIERARCHICAL_RUBRIC.equals(rubricRevision) ? HierarchicalScorePolicy.SHA256 : POLICY_SHA256).equals(rubricSha256) || promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")
                || expectedPhoneCount < 1 || expectedPhoneCount > 4096 || overallScore == null
                || alignmentScore != null || coverageScore != null || stabilityScore != null
                || criteria == null || criteria.size() != POLICY.get("criteria").size()) invalid();
        int weightedLevels = 0, activeWeight = 0;
        int[] totals = new int[6];
        for (int i = 0; i < criteria.size(); i++) {
            var item = criteria.get(i);
            var rule = POLICY.get("criteria").get(i);
            if (item == null || !rule.get("id").asText().equals(item.criterionId())
                    || item.sampleCount() < 0 || item.sampleCount() > expectedPhoneCount) invalid();
            int[] counts = item.counts();
            int n = 0;
            for (int count : counts) { if (count < 0 || count > item.sampleCount()) invalid(); n += count; }
            if (n != item.sampleCount()) invalid();
            boolean coverage = "coverage".equals(item.criterionId());
            if (coverage) {
                if (n != expectedPhoneCount || !java.util.Arrays.equals(totals, counts)) invalid();
            } else { for (int j = 0; j < counts.length; j++) totals[j] += counts[j]; }
            if (n == 0) { if (item.level() != null) invalid(); continue; }
            int units = coverage ? 4 * (n - item.deletedClear() - item.deletedFlagged())
                    : 4 * item.matchedClear() + 2 * item.matchedFlagged()
                        + 3 * item.substitutedClear() + item.substitutedFlagged();
            int expectedLevel = -1;
            for (var band : POLICY.get("levels")) {
                if (units * 100 >= band.get("minimumPercent").asInt() * 4 * n) {
                    expectedLevel = band.get("level").asInt(); break;
                }
            }
            if (item.level() == null || item.level() != expectedLevel) invalid();
            int weight = rule.get("weight").asInt();
            weightedLevels += weight * expectedLevel; activeWeight += weight;
        }
        if (correctPhoneCount != totals[0] + totals[1]
                || alignedPhoneCount != totals[0] + totals[1] + totals[2] + totals[3]
                || unflaggedPhoneCount != totals[0] + totals[2] + totals[4] || activeWeight == 0) invalid();
        BigDecimal expected = BigDecimal.valueOf(100L * weightedLevels)
                .divide(BigDecimal.valueOf(4L * activeWeight), 1, RoundingMode.HALF_UP);
        if (overallScore.compareTo(expected) != 0) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("invalid CLOVA detailed rubric score"); }
    private BigDecimal component(int count, int weight) {
        return BigDecimal.valueOf((long) count * weight)
                .divide(BigDecimal.valueOf(expectedPhoneCount), 1, RoundingMode.HALF_UP);
    }

    /** Public projection uses the same pinned rules as callback validation. */
    public AnalysisScoreBreakdown breakdown(BigDecimal overallScore) {
        validate(overallScore);
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
