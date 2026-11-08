package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compatibility view plus the complete received result document. No projection allowlist is enforced. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CanonicalAnalysisView(
        String schemaVersion, long analysisId, long recordingId, UUID requestId, UUID executionId,
        AnalysisStatus jobStatus, String analysisProfile, Canonical canonicalAnalysis,
        ServiceFailure serviceFailure, Actions actions, @JsonInclude(JsonInclude.Include.NON_NULL) String persistenceStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String,Object> rawResult) {
    public CanonicalAnalysisView(String schemaVersion,long analysisId,long recordingId,UUID requestId,UUID executionId,
            AnalysisStatus jobStatus,String analysisProfile,Canonical canonicalAnalysis,ServiceFailure serviceFailure,Actions actions,String persistenceStatus) {
        this(schemaVersion,analysisId,recordingId,requestId,executionId,jobStatus,analysisProfile,canonicalAnalysis,serviceFailure,actions,persistenceStatus,null);
    }
    public CanonicalAnalysisView withRawResult(Map<String,Object> raw) {
        return new CanonicalAnalysisView(schemaVersion,analysisId,recordingId,requestId,executionId,jobStatus,analysisProfile,
                canonicalAnalysis,serviceFailure,actions,persistenceStatus,raw);
    }
    public CanonicalAnalysisView(String schemaVersion,long analysisId,long recordingId,UUID requestId,UUID executionId,
            AnalysisStatus jobStatus,String analysisProfile,Canonical canonicalAnalysis,ServiceFailure serviceFailure,Actions actions){
        this(schemaVersion,analysisId,recordingId,requestId,executionId,jobStatus,analysisProfile,canonicalAnalysis,serviceFailure,actions,
                null);
    }
    public static final String SCHEMA_VERSION = "voice-coaching.canonical-analysis-view.v1";
    public static final String PROFILE = "CANONICAL_FROZEN_20260928_V4";

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Canonical(String representation, Decision decision, String coreStatus,
                            boolean feedbackDeliveryAllowed, String pronunciationFeedbackSource,
                            Selection selection, Coaching coaching, Score score, Visual visual) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Decision(String status, @JsonProperty("reason_code") String reasonCode, String stage) {}
    public record ServiceFailure(String origin, String code, String stage) {}
    public record Selection(String attemptScope, Coverage coverage, Map<String,Long> reviewReasonCounts,
                            List<Candidate> candidates) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Coverage(long evaluatedConsonantPositions, long visiblePositionCount, long permittedPatternCount,
                           List<String> includedCandidateIds, List<String> omittedCandidateIds, String selectionPolicy,
                           String omittedReason, Map<String,Long> presentationStateCounts) {}
    public record Candidate(String candidateId, List<String> evidenceIds, String expectedPhone,
                            String observedCandidate, String expectedRole, String roleSemantics,
                            long repetitionCount, String claimScope, String disposition, String presentationState,
                            Guidance guidance, List<Fact> facts) {}
    public record Guidance(String guidanceId, String revision, String scope, String action, String practice, String selfCheck) {}
    public record Fact(String evidenceId, long expectedIndex, Location location, String localizationStatus) {}
    public record Location(String word, long wordIndex, BigDecimal wordStartS, BigDecimal wordEndS,
                           BigDecimal phoneStartS, BigDecimal phoneEndS, String timingProvenance) {}
    public record Expression(String candidateId, String guidanceId, String explanation,
                             String action, String practice, String selfCheck) {}
    public record CoachingItem(Candidate candidate, Expression expression) {}
    public sealed interface Coaching permits ReadyCoaching, EmptyCoaching, FailedCoaching {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReadyCoaching(String schemaVersion, String adapterStatus, String generationStatus,
                                List<CoachingItem> items, int dispatchAttempts, String fallbackReason,
                                boolean naturalLanguageSemanticsFullyVerified,
                                boolean visualCorrectiveClaimsAllowed,
                                @JsonInclude(JsonInclude.Include.NON_NULL) String feedback) implements Coaching {
        public ReadyCoaching(String schemaVersion, String adapterStatus, String generationStatus,
                             List<CoachingItem> items, int dispatchAttempts, String fallbackReason,
                             boolean naturalLanguageSemanticsFullyVerified, boolean visualCorrectiveClaimsAllowed) {
            this(schemaVersion, adapterStatus, generationStatus, items, dispatchAttempts, fallbackReason,
                    naturalLanguageSemanticsFullyVerified, visualCorrectiveClaimsAllowed, null);
        }
    }
    public record EmptyCoaching(String schemaVersion, String adapterStatus, String generationStatus,
                                List<CoachingItem> items, int dispatchAttempts) implements Coaching {}
    public record FailedCoaching(String schemaVersion, String adapterStatus, String generationStatus,
                                 List<CoachingItem> items, int dispatchAttempts, String errorCode) implements Coaching {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Score(BigDecimal overallScore, String validity, String reason,
                        @JsonInclude(JsonInclude.Include.NON_NULL)
                        Object rubricRevision,
                        @JsonInclude(JsonInclude.Include.NON_NULL)
                        Object criteria,
                        @JsonInclude(JsonInclude.Include.NON_NULL)
                        ScoreDetails details) {
        public Score(BigDecimal overallScore, String validity, String reason,
                     Object rubricRevision, Object criteria) {
            this(overallScore, validity, reason, rubricRevision, criteria, null);
        }
    }
    /** AI-owned display values; consumers do not recompute or verify the worksheet. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ScoreDetails(String schemaVersion, String validationStatus, String aggregationMode,
                               BigDecimal audioScore, BigDecimal visualScore,
                               Integer audioWeight, Integer visualWeight,
                               List<ScoreDetailCriterion> criteria) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ScoreDetailCriterion(String criterionId, String label, BigDecimal score,
                                       String status, String reasonCode, Integer weight) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ScoreCriterion(String criterionId, Integer level) {}
    public sealed interface Visual permits AudioVisual, LipVisual {
        String status();
        boolean correctiveClaimsAllowed();
    }
    public record AudioVisual(String status, boolean correctiveClaimsAllowed) implements Visual {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LipVisual(String status, boolean correctiveClaimsAllowed, BigDecimal score, String scoreKind,
            LipCoverage coverage, List<LipPhone> phoneAssessments, List<String> reasonCodes,
            String alignmentScope, List<Long> unresolvedExpectedIndices, int omittedPhoneAssessmentCount,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<LipSubScore> subScores) implements Visual {
        /** Existing audio-only callers omit optional visual sub-scores. */
        public LipVisual(String status, boolean correctiveClaimsAllowed, BigDecimal score, String scoreKind,
                LipCoverage coverage, List<LipPhone> phoneAssessments, List<String> reasonCodes,
                String alignmentScope, List<Long> unresolvedExpectedIndices, int omittedPhoneAssessmentCount) {
            this(status, correctiveClaimsAllowed, score, scoreKind, coverage, phoneAssessments, reasonCodes,
                    alignmentScope, unresolvedExpectedIndices, omittedPhoneAssessmentCount, null);
        }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LipCoverage(long targetCount, long visuallyEligibleCount, long observedCount,
                              long scoredCount, BigDecimal coverage) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LipPhone(long expectedIndex, String expectedPhone, String role, String status,
            BigDecimal score, String scoreKind, BigDecimal referenceDistance, List<String> referenceIds,
            String calibrationRevision, BigDecimal audioStartSeconds, BigDecimal audioEndSeconds,
            BigDecimal videoStartSeconds, BigDecimal videoEndSeconds, int frameCount,
            List<String> reasonCodes, List<LipObservation> observations, List<String> allowedActionIds,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<LipSubScore> subScores) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LipSubScore(String criterionId, String label, BigDecimal score, String status,
                              String reasonCode,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<LipMeasurement> measurements) {}
    public record LipMeasurement(String metricId, BigDecimal value, BigDecimal referenceMedian,
                                 BigDecimal referenceScale, Long referenceCount, BigDecimal tolerance, String unit) {}
    public record LipObservation(String observationId, String code, BigDecimal minimum, BigDecimal maximum, String unit) {}
    public record Actions(boolean canRetry, boolean canRerecord, boolean canComplete, boolean canRegenerate,
                          UnavailableReasons unavailableReasonCodes) {}
    public record UnavailableReasons(List<String> retry, List<String> rerecord,
                                     List<String> complete, List<String> regenerate) {}
}
