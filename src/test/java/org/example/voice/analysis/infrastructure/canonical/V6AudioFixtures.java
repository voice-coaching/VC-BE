package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import java.util.UUID;

/** Synthetic protocol fixtures only; no model output or evidence-verification claim. */
public final class V6AudioFixtures {
    private V6AudioFixtures() {}
    public static ObjectNode failure() throws Exception {
        return (ObjectNode)new ObjectMapper().readTree("""
            {"schemaVersion":"voice-coaching.runpod-analysis-result.v6",
             "eventId":"44444444-4444-4444-8444-444444444444",
             "requestId":"11111111-1111-4111-8111-111111111111",
             "executionId":"22222222-2222-4222-8222-222222222222",
             "workerInstanceId":"33333333-3333-4333-8333-333333333333",
             "analysisId":1,"recordingId":2,"contentId":3,"status":"FAILED",
             "failure":{"origin":"INTEGRATION","code":"CANONICAL_EXECUTION_FAILED","stage":"EXECUTION"},
             "workerRevision":"fixture-worker","pipelineRevision":"fixture-pipeline",
             "analysisProfile":"CANONICAL_AUDIOVISUAL_20261007_V6",
             "sourceIdentity":{"scriptSha256":"%s","audioSha256":"%s",
               "preparedWavSha256":null,"canonicalAnalysisId":null,"coreSha256":null,
               "parentManifestSha256":"95f2347ad53ab53030f68a65795cfc5a84ca1c8c41bd03119c9a9fb15d0367c2",
               "llmManifestSha256":"9f10296b6944249ded9f5ce2ccfdfa7c53b6e4af670999fe68e9e477e97eaf45",
               "llmDependencyLockSha256":"93636f0c4befe7f1e34358e74b1077cc94cb2b82064781485b0183c53357d2f5",
               "promptSha256":"6f9e8515ae083a62e0500a03e3c2cef073ae78eb696bed8b5857a14b551ff1fd"},
             "canonical":null,"selection":null,"coaching":null,
             "visual":{"status":"NOT_PROVIDED","correctiveClaimsAllowed":false,"score":null,"scoreKind":"NONE",
               "coverage":{"targetCount":0,"visuallyEligibleCount":0,"observedCount":0,"scoredCount":0,"coverage":null},
               "phoneAssessments":[],"reasonCodes":["VIDEO_NOT_PROVIDED"],
               "alignmentScope":"CANONICAL_MFA_DETECTOR_PHONES_V1","unresolvedExpectedIndices":[],"omittedPhoneAssessmentCount":0},
             "prosodyPolicy":{"expressionPermission":"NO_APPROVED_GUIDANCE_MAPPING"},
             "score":{"overallScore":null,"validity":"NOT_AVAILABLE","reason":"NO_APPROVED_SCORING_CONTRACT"},
             "mediaBinding":{"videoSha256":null,"mediaReceiptSha256":null,"visualEvidenceSha256":null}}
            """.formatted("a".repeat(64),"b".repeat(64)));
    }
    public static ObjectNode handoff(ObjectNode projection) {
        var mapper=new ObjectMapper();var contract=new RunPodContract();
        var meta=mapper.createObjectNode().put("schemaVersion",CanonicalHandoffReadiness.VERSION)
                .put("handoffId",UUID.randomUUID().toString()).put("requestSha256","c".repeat(64))
                .put("projectionSha256",contract.digest(projection));
        for(var field:java.util.List.of("eventId","requestId","executionId","workerInstanceId","analysisId","recordingId","contentId"))
            meta.set(field,projection.get(field));
        meta.putArray("artifacts");
        var doc=mapper.createObjectNode();doc.set("metadata",meta);doc.set("projection",projection);
        doc.put("handoffSha256",contract.digest(meta));doc.putObject("inlineArtifacts");return doc;
    }
    public static ObjectNode accepted() throws Exception {
        var mapper=new ObjectMapper();var p=failure();p.put("status","COMPLETED");p.putNull("failure");
        var validation=mapper.readTree("""
            {"status":"ACCEPT","reason_code":null,"stage":null,"evidence":{},
             "source_provenance":null,"original_error":null,"subject":"RECORDING","decision_basis":null}
            """);
        var core=p.putObject("canonical").put("schemaVersion","canonical_ai_predeploy_v1")
                .put("representation","INLINE").put("coreStatus","ok").put("feedbackDeliveryAllowed",true)
                .put("pronunciationFeedbackSource","REFINED_P1_ONLY");
        core.set("inputValidation",validation);core.putObject("decision").put("status","ACCEPT").putNull("reason_code").putNull("stage");
        var hash=CanonicalCallbackDocument.sha256("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var identity=(ObjectNode)p.get("sourceIdentity");identity.put("coreSha256",hash).put("canonicalAnalysisId","fixture-analysis");
        var evidence=core.putObject("evidence").put("artifactKind","CORE").put("sha256",hash);
        evidence.set("pointers",mapper.readTree("""
            {"pronunciation":"/pronunciation_evidence","prosody":"/core_result/prosody","mfa":"/mfa_receipt",
             "contentGate":"/content_gate","inputValidation":"/input_validation","core":"/core_result","llmFacingEvidence":"/llm_facing_evidence"}
            """));
        var selection=p.putObject("selection").put("attemptScope","a".repeat(64));
        selection.putObject("reviewReasonCounts");selection.putArray("candidates");
        var coverage=selection.putObject("coverage").put("evaluatedConsonantPositions",0).put("visiblePositionCount",0)
                .put("permittedPatternCount",0).put("selectionPolicy","repetition_then_first_occurrence_max3").putNull("omittedReason");
        coverage.putArray("includedCandidateIds");coverage.putArray("omittedCandidateIds");coverage.putObject("presentationStateCounts");
        var coaching=p.putObject("coaching").put("schemaVersion","canonical-llm-expression-result-v1").put("coreSha256",hash)
                .put("adapterStatus","READY").put("generationStatus","SCHEMA_AND_STRUCTURAL_SEMANTICS_VALID")
                .put("dispatchAttempts",1).putNull("fallbackReason").put("naturalLanguageSemanticsFullyVerified",false)
                .put("visualCorrectiveClaimsAllowed",false);
        coaching.set("coreInputValidation",validation);coaching.putArray("items");
        ((ObjectNode)p.get("score")).put("validity","NOT_CALIBRATED");
        return p;
    }
}
