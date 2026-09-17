package org.example.voice.analysis.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** An auditable product rubric, not a calibrated pronunciation probability. */
public record ClovaScoreEvidence(String rubricRevision, String generator, String modelRevision,
        String evidenceSha256, int expectedPhoneCount, int alignedPhoneCount,
        int correctPhoneCount, int unflaggedPhoneCount,
        BigDecimal alignmentScore, BigDecimal coverageScore, BigDecimal stabilityScore) {
    public static final String RUBRIC = "clova-phone-rubric-v1";
    public void validate(BigDecimal overallScore) {
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
                || overallScore.compareTo(alignmentScore.add(coverageScore).add(stabilityScore)) != 0) {
            throw new IllegalArgumentException("invalid CLOVA rubric score");
        }
    }
    private BigDecimal component(int count, int weight) {
        return BigDecimal.valueOf((long) count * weight)
                .divide(BigDecimal.valueOf(expectedPhoneCount), 1, RoundingMode.HALF_UP);
    }
    public Map<String, Object> audit() {
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("rubricRevision", rubricRevision); values.put("generator", generator);
        values.put("modelRevision", modelRevision); values.put("evidenceSha256", evidenceSha256);
        values.put("expectedPhoneCount", expectedPhoneCount); values.put("alignedPhoneCount", alignedPhoneCount);
        values.put("correctPhoneCount", correctPhoneCount); values.put("unflaggedPhoneCount", unflaggedPhoneCount);
        values.put("alignmentScore", alignmentScore); values.put("coverageScore", coverageScore);
        values.put("stabilityScore", stabilityScore);
        return values;
    }
}
