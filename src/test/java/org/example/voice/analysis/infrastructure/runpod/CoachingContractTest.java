package org.example.voice.analysis.infrastructure.runpod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.voice.analysis.domain.model.AnalysisCoaching;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Synthetic protocol data, not an inference or pronunciation-quality test. */
public class CoachingContractTest {
    private final RunPodContract contract = new RunPodContract();

    public static String coaching() {
        return """
            {"schemaVersion":"voice-coaching.coaching-result.v1","status":"READY",
             "summary":"연습 안내","strengths":[],"items":[{
                "candidateId":"candidate-0","guidanceId":"practice-v1","explanation":"모델의 확인 후보입니다.",
                "action":"단어를 천천히 이어 말해 보세요.","practice":"같은 단어를 두 번 비교해 보세요.",
                "selfCheck":"앞뒤 소리가 유지되는지 들어 보세요.","evidenceIds":["phone-0"],
                "expectedPhone":"ㅏ","observedCandidate":"ㅓ",
                "location":{"word":"가","syllable":"가","charStart":0,"charEnd":1,
                    "writtenRole":"nucleus","roleSemantics":"written_not_post_phonology",
                    "startMs":100,"endMs":200,"timingProvenance":"CTC_NONBLANK_SPAN"},
                "observation":"모델 후보와 목표 표지가 다릅니다.",
                "claimScope":"MODEL_OBSERVATION_NOT_CONFIRMED_ARTICULATION"}],
             "practicePlan":"단어를 이어 말해 보세요.","limitations":[],"comparison":null,
             "coverage":{"expectedPhoneCount":3,"actionableCandidateCount":1,"reviewOnlyPhoneCount":1,
                "alignmentCheck":"CHECKED","includedCandidateIds":["candidate-0"],"omittedCandidateIds":[],
                "selectionPolicy":"repetition_then_first_occurrence","omittedReason":null},
             "score":{"validity":"INSUFFICIENT_EVIDENCE","overallScore":null,"reasonCodes":["ALIGNMENT_AMBIGUOUS"]},
             "visual":{"status":"NOT_PROVIDED","observations":[],"referenceStatus":"NOT_VALIDATED","correctiveClaimsAllowed":false},
             "generation":{"source":"TEMPLATE","provider":"NONE","promptRevision":"pronunciation-coaching-v1",
                "promptSha256":"%s","policyRevision":"pronunciation-coaching-evidence-v1","fallbackReason":"GENERATOR_NOT_CONFIGURED"}}
            """.formatted("a".repeat(64));
    }

    public static String result(UUID event) {
        return RunPodContractTest.result(event)
                .replace("runpod-analysis-result.v1", "runpod-analysis-result.v3")
                .replace("\"failureReason\":null", "\"failureReason\":null,\"overallScore\":null,\"scoringEvidence\":null,\"coaching\":" + coaching());
    }

    @Test
    void acceptsVersionedCoachingAndKeepsLegacyVersionStrict() {
        String body = result(UUID.randomUUID());
        contract.parse(body.getBytes(StandardCharsets.UTF_8), "result");
        for (String invalid : new String[] {
                body.replace("runpod-analysis-result.v3", "runpod-analysis-result.v1"),
                body.replace("runpod-analysis-result.v3", "runpod-analysis-result.v2"),
                body.replace("\"correctiveClaimsAllowed\":false", "\"correctiveClaimsAllowed\":true"),
                body.replace("\"overallScore\":null", "\"overallScore\":90"),
                body.replace("\"comparison\":null", "\"comparison\":{\"improved\":true}"),
                body.replace("\"strengths\":[]", "\"strengths\":[\"unsupported strength\"]")}) {
            assertThatThrownBy(() -> contract.parse(invalid.getBytes(StandardCharsets.UTF_8), "result"))
                    .isInstanceOf(RunPodContractException.class);
        }
    }

    @Test
    void validatesReferencesTimesAndGenerationBeforeStorage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.readValue(coaching(), AnalysisCoaching.class).validate(1000);
        var stored = mapper.readValue(coaching(), AnalysisCoaching.class);
        var cache = new org.example.voice.common.config.CacheConfig(java.util.List.of())
                .redisCacheConfiguration().getValueSerializationPair();
        assertThat(cache.read(cache.write(stored))).isEqualTo(stored);
        for (String invalid : new String[] {
                coaching().replace("phone-0", "phone-3"),
                coaching().replace("\"endMs\":200", "\"endMs\":1200"),
                coaching().replace("\"endMs\":200", "\"endMs\":50"),
                coaching().replace("\"startMs\":100", "\"startMs\":null"),
                coaching().replace("\"includedCandidateIds\":[\"candidate-0\"]", "\"includedCandidateIds\":[]"),
                coaching().replace("\"source\":\"TEMPLATE\"", "\"source\":\"LLM\""),
                coaching().replace("\"status\":\"READY\"", "\"status\":\"NO_ACTIONABLE_ISSUE\"")}) {
            assertThatThrownBy(() -> mapper.readValue(invalid, AnalysisCoaching.class).validate(1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void enforcesUtf16LengthInsideArraysAndDigestCoversCoaching() throws Exception {
        var body = (ObjectNode) new ObjectMapper().readTree(result(UUID.randomUUID()));
        ((ObjectNode) body.path("coaching").path("items").get(0)).put("action", "😀".repeat(601));
        assertThatThrownBy(() -> contract.parse(body.toString().getBytes(StandardCharsets.UTF_8), "result"))
                .isInstanceOf(RunPodContractException.class);
        String original = result(UUID.randomUUID());
        assertThat(contract.digest(original)).isNotEqualTo(contract.digest(original.replace("연습 안내", "변경 안내")));
    }
}
