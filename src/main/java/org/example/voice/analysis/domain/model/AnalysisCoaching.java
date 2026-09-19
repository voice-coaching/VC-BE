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

    public void validate(Integer durationMs) {
        if (!"voice-coaching.coaching-result.v1".equals(schemaVersion) || summary == null
                || summary.isBlank() || items == null || items.size() > 3 || coverage == null
                || score == null || score.overallScore() != null || generation == null
                || visual == null || visual.observations() == null || visual.correctiveClaimsAllowed()
                || strengths == null || !strengths.isEmpty() || limitations == null || comparison != null) invalid();
        if (coverage.expectedPhoneCount() < 1 || coverage.reviewOnlyPhoneCount() < 0
                || coverage.reviewOnlyPhoneCount() > coverage.expectedPhoneCount()
                || coverage.includedCandidateIds() == null || coverage.omittedCandidateIds() == null) invalid();
        Set<String> included = new HashSet<>(coverage.includedCandidateIds());
        Set<String> omitted = new HashSet<>(coverage.omittedCandidateIds());
        if (included.size() != coverage.includedCandidateIds().size()
                || omitted.size() != coverage.omittedCandidateIds().size()
                || included.stream().anyMatch(omitted::contains)
                || included.size() + omitted.size() != coverage.actionableCandidateCount()
                || omitted.isEmpty() != (coverage.omittedReason() == null)) invalid();
        Set<String> actual = new HashSet<>();
        Set<String> usedEvidence = new HashSet<>();
        for (Item item : items) {
            if (item == null || item.candidateId() == null || !actual.add(item.candidateId()) || item.evidenceIds() == null
                    || item.evidenceIds().isEmpty() || item.location() == null) invalid();
            for (String id : item.evidenceIds()) {
                if (id == null || !usedEvidence.add(id) || !id.matches("phone-[0-9]{1,4}")
                        || Integer.parseInt(id.substring(6)) >= coverage.expectedPhoneCount()) invalid();
            }
            if (!item.candidateId().equals(item.evidenceIds().getFirst().replace("phone-", "candidate-"))) invalid();
            Location location = item.location();
            if ((location.startMs() == null) != (location.endMs() == null)) invalid();
            if (location.startMs() != null && (durationMs == null || location.startMs() < 0
                    || location.endMs() <= location.startMs() || location.endMs() > durationMs
                    || !"CTC_NONBLANK_SPAN".equals(location.timingProvenance()))) invalid();
            if (location.startMs() == null && !"UNAVAILABLE".equals(location.timingProvenance())) invalid();
            if ((location.charStart() == null) != (location.charEnd() == null)
                    || (location.charStart() != null && location.charEnd() <= location.charStart())) invalid();
        }
        if (!actual.equals(included) || items.isEmpty() != (practicePlan == null)) invalid();
        String expected = !items.isEmpty() ? "READY" : coverage.reviewOnlyPhoneCount() > 0
                ? "LIMITED_EVIDENCE" : "NO_ACTIONABLE_ISSUE";
        if (!expected.equals(status)) invalid();
        if (("LLM".equals(generation.source())) != (generation.fallbackReason() == null)) invalid();
        if ("LLM".equals(generation.source()) && (items.isEmpty() || "NONE".equals(generation.provider()))) invalid();
        if (coverage.reviewOnlyPhoneCount() > 0 && !"INSUFFICIENT_EVIDENCE".equals(score.validity())) invalid();
        Set<Integer> observedIndices = new HashSet<>();
        for (VisualObservation observation : visual.observations()) {
            if (observation == null || observation.decodedSampleTimesMs() == null || observation.measurements() == null
                    || !observedIndices.add(observation.expectedIndex()) || observation.expectedIndex() < 0
                    || observation.expectedIndex() >= coverage.expectedPhoneCount()) invalid();
            BigDecimal previous = BigDecimal.valueOf(-1);
            for (BigDecimal time : observation.decodedSampleTimesMs()) {
                if (time == null || time.signum() < 0 || time.compareTo(previous) <= 0) invalid();
                previous = time;
            }
            for (VisualMeasurement measurement : observation.measurements()) {
                if (measurement == null || measurement.value() == null || measurement.startMs() < 0
                        || measurement.endMs() <= measurement.startMs() || measurement.endMs() > 180000) invalid();
            }
        }
    }

    private static void invalid() { throw new IllegalArgumentException("invalid coaching document"); }
}
