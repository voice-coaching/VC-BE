package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.example.voice.analysis.controller.dto.AnalysisResultResponseDto;
import org.example.voice.analysis.controller.dto.AnalysisStatusResponseDto;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.AnalysisResultReaderImpl;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalysisScoreBreakdownTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ClovaScoreEvidence evidence(boolean deleted) {
        var items = new ArrayList<ClovaScoreEvidence.Criterion>();
        for (String id : java.util.List.of("vowels", "plain_stops", "tense_stops", "aspirated_stops",
                "fricatives", "affricates", "nasals", "liquid", "coverage")) {
            boolean active = id.equals("vowels") || id.equals("coverage");
            items.add(new ClovaScoreEvidence.Criterion(id, active ? 4 : 0,
                    active && !deleted ? 3 : 0, active && !deleted ? 1 : 0,
                    0, 0, active && deleted ? 4 : 0, 0,
                    active ? (deleted ? 0 : id.equals("coverage") ? 4 : 3) : null));
        }
        return new ClovaScoreEvidence(ClovaScoreEvidence.DETAILED_RUBRIC, "hyperclova",
                "a".repeat(40), "b".repeat(64), 4, deleted ? 0 : 4, deleted ? 0 : 4,
                deleted ? 4 : 3, null, null, null, ClovaScoreEvidence.POLICY_SHA256,
                "c".repeat(64), items);
    }

    private Map<String, Object> stored(boolean deleted) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(evidence(deleted).audit()), new TypeReference<>() {});
    }

    @Test void projectsNineCriteriaAndExcludesAbsentWeights() throws Exception {
        var result = AnalysisScoreBreakdown.fromAudit(stored(false), new BigDecimal("82.1"));
        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(9);
        assertThat(result.items()).extracting(AnalysisScoreBreakdown.Item::maxScore)
                .containsExactly(25, 10, 10, 10, 10, 10, 10, 5, 10);
        assertThat(result.applicableMaxScore()).isEqualTo(35);
        var vowel = result.items().getFirst();
        assertThat(vowel.score()).isEqualByComparingTo("18.75");
        assertThat(vowel.expectedPhones()).hasSize(21);
        assertThat(result.items().get(8).score()).isEqualByComparingTo("10");
        var absent = result.items().get(1);
        assertThat(absent.applicable()).isFalse();
        assertThat(absent.score()).isNull();
        assertThat(absent.level()).isNull();
        assertThat(absent.description()).contains("예사소리");
        BigDecimal sum = result.items().stream().filter(AnalysisScoreBreakdown.Item::applicable)
                .map(AnalysisScoreBreakdown.Item::score).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.multiply(BigDecimal.valueOf(100)).divide(
                BigDecimal.valueOf(result.applicableMaxScore()), 1, RoundingMode.HALF_UP))
                .isEqualByComparingTo("82.1");
    }

    @Test void expectedButDeletedPhonesAreRealZeroScores() throws Exception {
        var result = AnalysisScoreBreakdown.fromAudit(stored(true), BigDecimal.ZERO);
        assertThat(result.items().getFirst().applicable()).isTrue();
        assertThat(result.items().getFirst().score()).isEqualByComparingTo("0");
        assertThat(result.items().get(1).score()).isNull();
    }

    @Test void legacyAndMissingEvidenceAreUnavailableNotZero() throws Exception {
        assertThat(AnalysisScoreBreakdown.fromAudit(null, BigDecimal.TEN)).isNull();
        assertThat(AnalysisScoreBreakdown.fromAudit(Map.of("rubricRevision", "clova-phone-rubric-v1"),
                BigDecimal.TEN)).isNull();
        assertThat(AnalysisScoreBreakdown.fromAudit(stored(false), null)).isNull();
    }

    @Test void invalidOrMismatchedEvidenceDoesNotInventBreakdown() throws Exception {
        assertThat(AnalysisScoreBreakdown.fromAudit(stored(false), BigDecimal.ZERO)).isNull();
        var audit = stored(false);
        audit.put("criteria", java.util.List.of());
        assertThat(AnalysisScoreBreakdown.fromAudit(audit, new BigDecimal("82.1"))).isNull();
        audit = stored(false);
        audit.put("rubricSha256", "0".repeat(64));
        assertThat(AnalysisScoreBreakdown.fromAudit(audit, new BigDecimal("82.1"))).isNull();
    }

    @Test void readerAndBothPublicDtosExposeOnlySafeProjection() throws Exception {
        var repository = mock(AnalysisResultJpaRepository.class);
        var entity = mock(AnalysisResult.class);
        when(entity.getId()).thenReturn(12L);
        when(entity.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(entity.getOverallScore()).thenReturn(new BigDecimal("82.1"));
        when(entity.getClovaScoreEvidence()).thenReturn(stored(false));
        when(repository.findByIdAndRecordingTrainingSessionUserId(12L, 17L))
                .thenReturn(java.util.Optional.of(entity));
        var data = new AnalysisResultReaderImpl(repository).findOwnedData(12L, 17L).orElseThrow();
        var detail = AnalysisResultResponseDto.from(data);
        var summary = AnalysisStatusResponseDto.from(30L, data);
        assertThat(detail.scoreBreakdown()).isEqualTo(summary.scoreBreakdown());
        assertThat(detail.scoreBreakdown().items()).hasSize(9);
        String json = mapper.writeValueAsString(detail);
        assertThat(json).contains("\"scoreBreakdown\"", "\"score\":18.75", "\"score\":null")
                .doesNotContain("evidenceSha256", "modelRevision", "promptSha256", "matchedClear");
        var pair = new org.example.voice.common.config.CacheConfig(java.util.List.of())
                .redisCacheConfiguration().getValueSerializationPair();
        var restored = (AnalysisResultData) pair.read(pair.write(data));
        assertThat(restored.scoreBreakdown()).isEqualTo(data.scoreBreakdown());
    }
}
