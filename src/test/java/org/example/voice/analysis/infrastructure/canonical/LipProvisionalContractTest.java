package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.example.voice.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class LipProvisionalContractTest {
    static final ObjectMapper MAPPER=new ObjectMapper();
    final RunPodContract contract=new RunPodContract();
    public static ObjectNode result() throws Exception {
        var root=V6AudioFixtures.accepted();
        root.set("visual",MAPPER.readTree("""
          {"status":"OBSERVED","correctiveClaimsAllowed":false,"score":null,"scoreKind":"NONE",
           "coverage":{"targetCount":2,"visuallyEligibleCount":2,"observedCount":1,"scoredCount":1,"coverage":0.5},
           "phoneAssessments":[{"expectedIndex":0,"expectedPhone":"p","role":"onset","status":"SCORED",
             "score":0,"scoreKind":"PROVISIONAL","referenceDistance":1,"referenceIds":["fixture-reference"],
             "calibrationRevision":null,"audioStartSeconds":0,"audioEndSeconds":0.2,
             "videoStartSeconds":0,"videoEndSeconds":0.2,"frameCount":6,"reasonCodes":[],"observations":[],
             "allowedActionIds":["COMPARE_REFERENCE"],"subScores":[
               {"criterionId":"lip_aperture","label":"aperture","score":0,"status":"SCORED","reasonCode":"REFERENCE_COMPARISON",
                "measurements":[{"metricId":"aperture","value":0,"referenceMedian":1,"referenceScale":1,"referenceCount":13,"tolerance":0,"unit":"FACE_SCALE_NORMALIZED"}]},
               {"criterionId":"lip_timing","label":"timing","score":null,"status":"UNSCORABLE","reasonCode":"NO_FRAME_IN_ALIGNED_SPAN"}]}],
           "reasonCodes":["INSUFFICIENT_COVERAGE"],"alignmentScope":"CANONICAL_MFA_DETECTOR_PHONES_V1",
           "unresolvedExpectedIndices":[1],"omittedPhoneAssessmentCount":0,
           "subScores":[{"criterionId":"lip_aperture","label":"aperture","score":0,"status":"SCORED","reasonCode":"REFERENCE_COMPARISON"}]}
          """));
        var media=(ObjectNode)root.get("mediaBinding");
        media.put("videoSha256","c".repeat(64)).put("mediaReceiptSha256","d".repeat(64)).put("visualEvidenceSha256","e".repeat(64));
        root.putObject("score").put("overallScore",73.6).put("validity","RUBRIC_COMPUTED").put("reason","EVIDENCE_BOUND_PRACTICE_SCORE");
        ((ObjectNode)root.get("coaching")).put("feedback","Keep the observed practice feedback.");
        return root;
    }
    static byte[] bytes(ObjectNode root) { return root.toString().getBytes(StandardCharsets.UTF_8); }
    @Test void partialCoverageKeepsAudioTotalFeedbackAndProvisionalLocalEvidence() throws Exception {
        var raw=result(); var doc=CanonicalCallbackDocument.parse(bytes(raw),contract);
        var projected=new CanonicalPublicProjection(contract).project(doc);
        assertThat(projected.score().overallScore()).isEqualByComparingTo("73.6");
        var visual=(CanonicalAnalysisView.LipVisual)projected.visual();
        assertThat(visual.score()).isNull(); assertThat(visual.correctiveClaimsAllowed()).isFalse();
        assertThat(visual.phoneAssessments().getFirst().scoreKind()).isEqualTo("PROVISIONAL");
        assertThat(visual.phoneAssessments().getFirst().calibrationRevision()).isNull();
        assertThat(visual.phoneAssessments().getFirst().allowedActionIds()).containsExactly("COMPARE_REFERENCE");
        assertThat(visual.phoneAssessments().getFirst().subScores().getFirst().score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(visual.phoneAssessments().getFirst().subScores().get(1).score()).isNull();
        assertThat(((CanonicalAnalysisView.ReadyCoaching)projected.coaching()).feedback()).isEqualTo("Keep the observed practice feedback.");
        assertThat(doc.bytes()).isEqualTo(bytes(raw));
    }
    @Test void publicWrapperPreservesNullScoresZeroMeasurementsAndOptionalOmission() throws Exception {
        var canonical=new CanonicalPublicProjection(contract).project(CanonicalCallbackDocument.parse(bytes(result()),contract));
        var mapper=new tools.jackson.databind.ObjectMapper();
        var json=mapper.readTree(mapper.writeValueAsString(ApiResponse.success("ok",canonical)));
        var phone=json.path("data").path("visual").path("phoneAssessments").get(0);
        assertThat(json.at("/data/visual/score").isNull()).isTrue();
        assertThat(phone.at("/subScores/0/score").decimalValue()).isEqualByComparingTo("0");
        assertThat(phone.at("/subScores/1/score").isNull()).isTrue();
        assertThat(phone.at("/subScores/1").has("measurements")).isFalse();
        assertThat(phone.at("/subScores/0/measurements/0/referenceCount").intValue()).isEqualTo(13);
    }
    @Test void oldAudioAndOldVisualWithoutOptionalSubscoresRemainReadable() throws Exception {
        var old=V6AudioFixtures.accepted();
        var view=new CanonicalPublicProjection(contract).project(CanonicalCallbackDocument.parse(bytes(old),contract));
        var json=MAPPER.valueToTree(view);
        assertThat(json.at("/visual/status").asText()).isEqualTo("NOT_PROVIDED");
        assertThat(json.at("/visual").has("subScores")).isFalse();
        var root=result();var visual=(ObjectNode)root.get("visual"); visual.remove("subScores");
        ((ObjectNode)visual.path("phoneAssessments").get(0)).remove("subScores");
        assertThatCode(()->new CanonicalPublicProjection(contract).project(CanonicalCallbackDocument.parse(bytes(root),contract))).doesNotThrowAnyException();
    }
    @Test void rootProvisionalScoreIsAcceptedWithoutInventingCalibration() throws Exception {
        var root=result(); ((ObjectNode)root.get("visual")).put("status","SCORED").put("score",80).put("scoreKind","PROVISIONAL");
        assertThatCode(()->CanonicalCallbackDocument.parse(bytes(root),contract)).doesNotThrowAnyException();
    }
    @Test void unscorableNumbersTooManySubscoresAndUnexpectedActionsAreRejected() throws Exception {
        var invalid=result(); ((ObjectNode)invalid.at("/visual/phoneAssessments/0/subScores/1")).put("score",10);
        assertThatThrownBy(()->contract.parse(bytes(invalid),"result")).isInstanceOf(RunPodContractException.class);
        var many=result();var scores=(com.fasterxml.jackson.databind.node.ArrayNode)many.at("/visual/subScores");
        while(scores.size()<5) scores.add(scores.get(0).deepCopy());
        assertThatThrownBy(()->contract.parse(bytes(many),"result")).isInstanceOf(RunPodContractException.class);
        var action=result(); ((ObjectNode)action.at("/visual/phoneAssessments/0")).putArray("allowedActionIds").add("CORRECT_PRONUNCIATION");
        assertThatThrownBy(()->contract.parse(bytes(action),"result")).isInstanceOf(RunPodContractException.class);
    }
    @Test void excessiveReferenceCountIsNotSilentlyAdjusted() throws Exception {
        var root=result();((ObjectNode)root.at("/visual/phoneAssessments/0/subScores/0/measurements/0")).put("referenceCount",33);
        assertThatThrownBy(()->contract.parse(bytes(root),"result")).isInstanceOf(RunPodContractException.class);
    }
}
