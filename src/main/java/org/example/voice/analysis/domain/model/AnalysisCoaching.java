package org.example.voice.analysis.domain.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stored teaching document; legacy selected-phone metadata is not its agenda. */
public record AnalysisCoaching(String schemaVersion, String status, String summary,
        List<String> strengths, List<Item> items, String practicePlan, List<String> limitations,
        String comparison, Coverage coverage, Score score, Visual visual, Generation generation) {
    public record Item(String candidateId, String guidanceId, String explanation, String action,
            String practice, String selfCheck, List<String> evidenceIds, String expectedPhone,
            String observedCandidate, Location location, String observation, String claimScope) { }
    public record Location(String word, String syllable, Integer charStart, Integer charEnd,
            String writtenRole, String roleSemantics, Integer startMs, Integer endMs,
            String timingProvenance) { }
    public record Coverage(int expectedPhoneCount, int actionableCandidateCount,
            int reviewOnlyPhoneCount, String alignmentCheck, List<String> includedCandidateIds,
            List<String> omittedCandidateIds, String selectionPolicy, String omittedReason) { }
    public record Score(String validity, BigDecimal overallScore, List<String> reasonCodes) { }
    public record Visual(String status, List<VisualObservation> observations, String referenceStatus,
            boolean correctiveClaimsAllowed) { }
    public record VisualObservation(int expectedIndex, String stage, List<String> reasonCodes,
            List<VisualMeasurement> measurements, List<BigDecimal> decodedSampleTimesMs, String samplingScope) { }
    public record VisualMeasurement(String cueId, BigDecimal value, String unit, int startMs,
            int endMs, Integer sampleCount, String samplingProvenance) { }
    public record Generation(String source, String provider, String promptRevision,
            String promptSha256, String policyRevision, String fallbackReason) { }

    public void validate(Integer durationMs) {}

}
