package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Only user-facing scores; internal hashes, model revisions and raw detector counts are not exposed. */
public record AnalysisScoreBreakdown(String rubricRevision, int applicableMaxScore, List<Item> items) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

    public record Item(String criterionId, String label, String description, List<String> expectedPhones,
                       int maxScore, boolean applicable, int sampleCount, Integer level, BigDecimal score) {}

    public static AnalysisScoreBreakdown fromAudit(Map<String, Object> audit, BigDecimal overallScore) {
        if (audit == null || overallScore == null
                || !ClovaScoreEvidence.DETAILED_RUBRIC.equals(audit.get("rubricRevision"))) return null;
        try {
            return MAPPER.convertValue(audit, ClovaScoreEvidence.class).breakdown(overallScore);
        } catch (IllegalArgumentException | NullPointerException invalidStoredEvidence) {
            // Legacy or invalid evidence must not be rendered as invented zero scores.
            return null;
        }
    }

    static String description(String id) {
        return switch (id) {
            case "vowels" -> "공기의 흐름을 크게 막지 않고 혀와 입술의 모양에 따라 내는 소리입니다. 현대 한글 모음 표지 21개를 대상으로 합니다.";
            case "plain_stops" -> "공기의 흐름을 막았다가 터뜨리면서 내는 예사소리(평음)입니다. ㄱ, ㄷ, ㅂ이 해당합니다.";
            case "tense_stops" -> "공기의 흐름을 막았다가 긴장감을 주어 터뜨리는 된소리(경음)입니다. ㄲ, ㄸ, ㅃ이 해당합니다.";
            case "aspirated_stops" -> "공기의 흐름을 막았다가 강한 숨과 함께 터뜨리는 거센소리(격음)입니다. ㅋ, ㅌ, ㅍ이 해당합니다.";
            case "fricatives" -> "좁아진 통로로 공기가 지나가며 마찰을 일으켜 내는 소리입니다. ㅅ, ㅆ, ㅎ이 해당합니다.";
            case "affricates" -> "공기를 막았다가 터뜨린 뒤 마찰을 이어 내는 소리입니다. ㅈ, ㅉ, ㅊ이 해당합니다.";
            case "nasals" -> "공기를 코로 내보내며 내는 소리입니다. ㄴ, ㅁ, 받침 ㅇ이 해당하며, 초성의 소리 없는 ㅇ 자체를 평가한다는 뜻은 아닙니다.";
            case "liquid" -> "혀끝을 잇몸에 가볍게 대거나 공기를 혀 옆으로 흘려 내는 ㄹ 계열의 소리입니다.";
            case "coverage" -> "대본에서 기대한 전체 음소 중 음성과 시간적으로 대응시킨 범위를 평가합니다. 모든 발음이 정확하다는 뜻은 아닙니다.";
            default -> throw new IllegalArgumentException("unknown pronunciation criterion");
        };
    }
}
