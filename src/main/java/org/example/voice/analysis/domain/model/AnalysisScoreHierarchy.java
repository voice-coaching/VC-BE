package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;

/** Safe public projection. Legacy aggregate counts cannot reconstruct phone scores. */
public record AnalysisScoreHierarchy(String rubricRevision, int leafCount, List<Group> groups) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    public record Group(String id, String label, String description, Integer maxScore, BigDecimal score, List<Item> items) {}
    public record Item(String id, String label, String description, String status, Integer sampleCount,
            Integer level, Integer normalizedScore, boolean attention, Observation observation) {}
    public record Observation(BigDecimal value, String unit, Integer selectedExpectedIndex, Integer videoStartMs, Integer videoEndMs) {}

    public static AnalysisScoreHierarchy fromAudit(Map<String, Object> audit, BigDecimal overallScore) {
        if (audit == null || overallScore == null || !ClovaScoreEvidence.HIERARCHICAL_RUBRIC.equals(audit.get("rubricRevision"))) return null;
        try {
            var evidence = MAPPER.convertValue(audit, ClovaScoreEvidence.class);
            evidence.validate(overallScore);
            var groups = new ArrayList<Group>();
            var rules = HierarchicalScorePolicy.POLICY;
            var breakdown = evidence.breakdown(overallScore);
            for (var parent : breakdown.items()) {
                var items = new ArrayList<Item>();
                for (int i = 0; i < rules.get("phoneCriteria").size(); i++) {
                    var rule = rules.get("phoneCriteria").get(i);
                    if (!parent.criterionId().equals(rule.get("parentId").asText())) continue;
                    var row = evidence.phoneCriteria().get(i);
                    items.add(audioItem(row, rule.get("label").asText(), evidence.attentionCriterionIds().contains(row.criterionId())));
                }
                if ("coverage".equals(parent.criterionId())) {
                    var row = evidence.criteria().get(evidence.criteria().size() - 1);
                    items.add(new Item("coverage.aligned", "전체 기대 음소 정렬 대응", parent.description(), "SCORED", row.sampleCount(),
                            row.level(), row.level() * 25, false, null));
                }
                groups.add(new Group(parent.criterionId(), parent.label(), parent.description(), parent.maxScore(), parent.score(), List.copyOf(items)));
            }
            var visual = evidence.visualObservation();
            for (String groupId : List.of("lip_shape", "lip_motion")) {
                var items = new ArrayList<Item>();
                for (var rule : rules.get("visualCriteria")) {
                    if (!groupId.equals(rule.get("parentId").asText())) continue;
                    var key = rule.get("measurementKey");
                    BigDecimal value = key.isNull() ? null : visual.measurements().get(key.asText());
                    String status = "NOT_PROVIDED".equals(visual.status()) ? "NOT_PROVIDED" : value != null ? "NOT_VALIDATED" : "UNAVAILABLE";
                    var observation = value == null ? null : new Observation(value, "ratio", visual.selectedExpectedIndex(), visual.videoStartMs(), visual.videoEndMs());
                    items.add(new Item(rule.get("id").asText(), rule.get("label").asText(), rule.get("description").asText(),
                            status, null, null, null, false, observation));
                }
                groups.add(new Group(groupId, "lip_shape".equals(groupId) ? "입술 형상" : "입술 움직임",
                        "영상은 선택된 음소 구간의 관찰이며 발음 적합성 기준이 검증되지 않아 총점에 포함하지 않습니다.", null, null, List.copyOf(items)));
            }
            return new AnalysisScoreHierarchy(evidence.rubricRevision(), 49, List.copyOf(groups));
        } catch (IllegalArgumentException | NullPointerException invalidStoredEvidence) { return null; }
    }

    private static Item audioItem(ClovaScoreEvidence.Criterion row, String label, boolean attention) {
        String description = row.sampleCount() == 0 ? "이 문장에는 해당 기대 음소가 없어 평가하지 않습니다."
                : "기대 표지 " + row.sampleCount() + "개 중 CTC 일치 후보 " + (row.matchedClear() + row.matchedFlagged())
                + "개, 대치 후보 " + (row.substitutedClear() + row.substitutedFlagged()) + "개, 누락 후보 "
                + (row.deletedClear() + row.deletedFlagged()) + "개입니다. 검출 표시 "
                + (row.matchedFlagged() + row.substitutedFlagged() + row.deletedFlagged())
                + "개이며, 사람의 확정 오류 판정이나 정확도 확률은 아닙니다.";
        return new Item(row.criterionId(), label, description, row.sampleCount() == 0 ? "NOT_APPLICABLE" : "SCORED",
                row.sampleCount(), row.level(), row.level() == null ? null : row.level() * 25, attention, null);
    }
}
