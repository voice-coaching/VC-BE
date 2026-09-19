package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnalysisScoreHierarchyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ClovaScoreEvidence fixture(boolean deleted) {
        var parents = new ArrayList<ClovaScoreEvidence.Criterion>();
        for (var rule : HierarchicalScorePolicy.POLICY.get("criteria")) {
            String id = rule.get("id").asText();
            boolean active = id.equals("vowels") || id.equals("coverage");
            parents.add(row(id, active, deleted, id.equals("coverage")));
        }
        var leaves = new ArrayList<ClovaScoreEvidence.Criterion>();
        for (var rule : HierarchicalScorePolicy.POLICY.get("phoneCriteria")) {
            leaves.add(row(rule.get("id").asText(), rule.get("phone").asText().equals("ㅏ"), deleted, false));
        }
        return new ClovaScoreEvidence(ClovaScoreEvidence.HIERARCHICAL_RUBRIC, "hyperclova", "a".repeat(40),
                "b".repeat(64), 4, deleted ? 0 : 4, deleted ? 0 : 4, deleted ? 4 : 3,
                null, null, null, HierarchicalScorePolicy.SHA256, "c".repeat(64), parents, leaves,
                new HierarchicalScorePolicy.VisualObservation("NOT_PROVIDED", null, null, null, null, Map.of()), List.of("vowels.a"));
    }
    private ClovaScoreEvidence.Criterion row(String id, boolean active, boolean deleted, boolean coverage) {
        return new ClovaScoreEvidence.Criterion(id, active ? 4 : 0, active && !deleted ? 3 : 0,
                active && !deleted ? 1 : 0, 0, 0, active && deleted ? 4 : 0, 0,
                active ? deleted ? 0 : coverage ? 4 : 3 : null);
    }
    private Map<String,Object> audit(boolean deleted) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(fixture(deleted).audit()), new TypeReference<>() {});
    }
    @Test void projectsFortyNineLeavesWithUnchangedParentTotalAndRealNulls() throws Exception {
        var result = AnalysisScoreHierarchy.fromAudit(audit(false), new BigDecimal("82.1"));
        assertThat(result).isNotNull();
        assertThat(result.groups()).hasSize(11);
        assertThat(result.groups().stream().mapToInt(g -> g.items().size()).sum()).isEqualTo(49);
        var vowel = result.groups().getFirst();
        assertThat(vowel.score()).isEqualByComparingTo("18.75");
        assertThat(vowel.items().getFirst().normalizedScore()).isEqualTo(75);
        assertThat(vowel.items().getFirst().attention()).isTrue();
        assertThat(vowel.items().get(1).status()).isEqualTo("NOT_APPLICABLE");
        assertThat(vowel.items().get(1).normalizedScore()).isNull();
        assertThat(result.groups().getLast().items()).allSatisfy(i -> {
            assertThat(i.status()).isEqualTo("NOT_PROVIDED");
            assertThat(i.normalizedScore()).isNull();
        });
        var cache = new org.example.voice.common.config.CacheConfig(List.of()).redisCacheConfiguration().getValueSerializationPair();
        assertThat(cache.read(cache.write(result))).isEqualTo(result);
        String json = mapper.writeValueAsString(result);
        assertThat(json).doesNotContain("Sha256", "modelRevision", "raw", "landmark");
    }
    @Test void missingExpectedPhoneAndDeletedExpectedPhoneAreDifferent() throws Exception {
        var result = AnalysisScoreHierarchy.fromAudit(audit(true), BigDecimal.ZERO);
        var leaf = result.groups().getFirst().items().getFirst();
        assertThat(leaf.status()).isEqualTo("SCORED");
        assertThat(leaf.normalizedScore()).isZero();
        assertThat(result.groups().getFirst().items().get(1).normalizedScore()).isNull();
    }
    @Test void cannotForgeLeafCountsOrAttentionOrPolicy() throws Exception {
        var evidence = audit(false);
        ((Map<String,Object>)((List<?>)evidence.get("phoneCriteria")).getFirst()).put("sampleCount", 3);
        assertThat(AnalysisScoreHierarchy.fromAudit(evidence, new BigDecimal("82.1"))).isNull();
        evidence = audit(false); evidence.put("attentionCriterionIds", List.of("vowels.i"));
        assertThat(AnalysisScoreHierarchy.fromAudit(evidence, new BigDecimal("82.1"))).isNull();
        evidence = audit(false); evidence.put("rubricSha256", "0".repeat(64));
        assertThat(AnalysisScoreHierarchy.fromAudit(evidence, new BigDecimal("82.1"))).isNull();
        assertThat(AnalysisScoreHierarchy.fromAudit(audit(false), BigDecimal.ZERO)).isNull();
    }
    @Test void visualMeasurementsAreNotScoresAndRequireBinding() throws Exception {
        var evidence = audit(false);
        evidence.put("visualObservation", Map.of("status", "NOT_VALIDATED", "selectedExpectedIndex", 0,
                "videoStartMs", 10, "videoEndMs", 30, "supplementSha256", "d".repeat(64),
                "measurements", Map.of("inner_aperture_ratio", 0.2)));
        var parsed = mapper.convertValue(evidence, ClovaScoreEvidence.class);
        parsed.validate(new BigDecimal("82.1"));
        var result = AnalysisScoreHierarchy.fromAudit(evidence, new BigDecimal("82.1"));
        var visual = result.groups().get(9).items().getFirst();
        assertThat(visual.status()).isEqualTo("NOT_VALIDATED");
        assertThat(visual.normalizedScore()).isNull();
        assertThat(visual.observation().value()).isEqualByComparingTo("0.2");
        assertThat(result.groups().get(10).items()).allSatisfy(i -> assertThat(i.status()).isEqualTo("UNAVAILABLE"));
        var worker = org.mockito.Mockito.mock(AnalysisWorkerResult.class);
        assertThatThrownBy(() -> HierarchicalScorePolicy.validateBinding(parsed, worker)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void doesNotInventLegacyHierarchy() {
        assertThat(AnalysisScoreHierarchy.fromAudit(null, BigDecimal.TEN)).isNull();
        assertThat(AnalysisScoreHierarchy.fromAudit(Map.of("rubricRevision", "clova-phone-rubric-v2"), BigDecimal.TEN)).isNull();
    }
}
