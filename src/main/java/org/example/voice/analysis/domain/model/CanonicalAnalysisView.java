package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public allowlist only. Never add raw core, private callback, receipt or storage fields. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CanonicalAnalysisView(
        String schemaVersion, long analysisId, long recordingId, UUID requestId, UUID executionId,
        AnalysisStatus jobStatus, String analysisProfile, Canonical canonicalAnalysis,
        ServiceFailure serviceFailure, Actions actions) {
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
                                boolean visualCorrectiveClaimsAllowed) implements Coaching {}
    public record EmptyCoaching(String schemaVersion, String adapterStatus, String generationStatus,
                                List<CoachingItem> items, int dispatchAttempts) implements Coaching {}
    public record FailedCoaching(String schemaVersion, String adapterStatus, String generationStatus,
                                 List<CoachingItem> items, int dispatchAttempts, String errorCode) implements Coaching {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Score(BigDecimal overallScore, String validity, String reason) {}
    public record Visual(String status, boolean correctiveClaimsAllowed) {}
    public record Actions(boolean canRetry, boolean canRerecord, boolean canComplete, boolean canRegenerate,
                          UnavailableReasons unavailableReasonCodes) {}
    public record UnavailableReasons(List<String> retry, List<String> rerecord,
                                     List<String> complete, List<String> regenerate) {}
}
