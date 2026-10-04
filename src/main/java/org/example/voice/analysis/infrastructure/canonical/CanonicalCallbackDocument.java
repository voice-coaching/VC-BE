package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * Schema-checked PRIVATE envelope, not proof of frozen semantics or a public DTO.
 * Operational fields are typed. Full frozen inputValidation/selection/coaching and
 * MFA decimals remain in the exact bytes, never a legacy worker-result conversion.
 * Only the offline verifier can promote this document to a committable inbox entry.
 */
@JsonIgnoreType
public final class CanonicalCallbackDocument {
    public enum DecisionStatus { ACCEPT, REJECT, INCONCLUSIVE, SYSTEM_FAILURE }
    public record Identity(UUID eventId, UUID requestId, UUID executionId, UUID workerId,
                           long analysisId, long recordingId, long contentId) {}
    public record Decision(DecisionStatus status, String reasonCode, String stage) {}
    public record Failure(String origin, String code, String stage) {}
    public record Source(String scriptSha256, String audioSha256, String preparedWavSha256,
                         String canonicalAnalysisId, String coreSha256, String parentManifestSha256,
                         String llmManifestSha256, String llmDependencyLockSha256, String promptSha256) {}
    public record Retention(UUID receiptId, String manifestSha256) {}

    private final byte[] raw;
    private final String schemaVersion, analysisProfile;
    private final String rawSha256, payloadSha256, workerRevision, pipelineRevision;
    private final Identity identity;
    private final AnalysisStatus status;
    private final Decision decision;
    private final Failure failure;
    private final Source source;
    private final Retention retention;
    private final String representation, coreStatus, adapterStatus, generationStatus;
    private final boolean feedbackDeliveryAllowed;
    private final List<String> coachingActions;
    private final java.math.BigDecimal overallScore;

    private CanonicalCallbackDocument(byte[] raw, JsonNode node, String digest) {
        this.raw = raw.clone();
        schemaVersion=node.path("schemaVersion").asText(); analysisProfile=node.path("analysisProfile").asText();
        rawSha256 = sha256(raw);
        payloadSha256 = digest;
        identity = new Identity(uuid(node,"eventId"),uuid(node,"requestId"),uuid(node,"executionId"),
                uuid(node,"workerInstanceId"),node.get("analysisId").longValue(),
                node.get("recordingId").longValue(),node.get("contentId").longValue());
        status = AnalysisStatus.valueOf(node.get("status").asText());
        workerRevision = node.get("workerRevision").asText();
        pipelineRevision = node.get("pipelineRevision").asText();
        var canonical = node.get("canonical");
        var d = canonical.path("decision");
        decision = canonical.isNull() ? null : new Decision(DecisionStatus.valueOf(d.get("status").asText()),
                nullable(d,"reason_code"),nullable(d,"stage"));
        representation = nullable(canonical,"representation");
        coreStatus = nullable(canonical,"coreStatus");
        feedbackDeliveryAllowed = canonical.path("feedbackDeliveryAllowed").asBoolean(false);
        var f = node.get("failure");
        failure = f.isNull() ? null : new Failure(f.get("origin").asText(),f.get("code").asText(),f.get("stage").asText());
        var s = node.get("sourceIdentity");
        source = new Source(s.get("scriptSha256").asText(),s.get("audioSha256").asText(),nullable(s,"preparedWavSha256"),
                nullable(s,"canonicalAnalysisId"),nullable(s,"coreSha256"),s.get("parentManifestSha256").asText(),
                s.get("llmManifestSha256").asText(),s.get("llmDependencyLockSha256").asText(),s.get("promptSha256").asText());
        var r = node.path("retainedEvidence");
        retention = (r.isNull() || r.isMissingNode()) ? null : new Retention(uuid(r,"evidenceReceiptId"),r.get("manifestSha256").asText());
        adapterStatus = nullable(node.get("coaching"),"adapterStatus");
        generationStatus = nullable(node.get("coaching"),"generationStatus");
        var actions = new ArrayList<String>();
        if ("READY".equals(adapterStatus)) {
            for (var item : node.get("coaching").get("items")) {
                actions.add(item.get("expression").get("action").textValue());
            }
        }
        coachingActions = List.copyOf(actions);
        overallScore = "RUBRIC_COMPUTED".equals(node.path("score").path("validity").asText())
                ? node.get("score").get("overallScore").decimalValue() : null;
    }

    public static CanonicalCallbackDocument parse(byte[] raw, RunPodContract contract) {
        var node = contract.parse(raw,"result");
        if (!java.util.Set.of(RunPodContract.RESULT_V4,RunPodContract.RESULT_V5).contains(node.path("schemaVersion").asText()))
            throw new RunPodContractException(422,"VALIDATION_FAILED");
        return new CanonicalCallbackDocument(raw,node,contract.digest(new String(raw,StandardCharsets.UTF_8)));
    }

    public String schemaVersion(){return schemaVersion;}
    public String analysisProfile(){return analysisProfile;}
    public byte[] bytes() { return raw.clone(); }
    public String storageJson() { return new String(raw,StandardCharsets.UTF_8); }
    public String rawSha256() { return rawSha256; }
    public String payloadSha256() { return payloadSha256; }
    public Identity identity() { return identity; }
    public AnalysisStatus status() { return status; }
    public Decision decision() { return decision; }
    public Failure failure() { return failure; }
    public Source source() { return source; }
    public Retention retention() { return retention; }
    public String representation() { return representation; }
    public String coreStatus() { return coreStatus; }
    public boolean feedbackDeliveryAllowed() { return feedbackDeliveryAllowed; }
    public List<String> coachingActions() { return coachingActions; }
    public java.math.BigDecimal overallScore() { return overallScore; }
    public String adapterStatus() { return adapterStatus; }
    public String generationStatus() { return generationStatus; }
    public String workerRevision() { return workerRevision; }
    public String pipelineRevision() { return pipelineRevision; }
    @Override public String toString() { return "CanonicalCallbackDocument[private]"; }

    static String sha256(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    private static UUID uuid(JsonNode n,String field) { return UUID.fromString(n.get(field).asText()); }
    private static String nullable(JsonNode n,String field) {
        var value=n.get(field);return value==null || value.isNull()?null:value.asText();
    }
}
