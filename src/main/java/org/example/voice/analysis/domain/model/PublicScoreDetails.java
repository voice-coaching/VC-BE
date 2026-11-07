package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** AI-produced display scores. No worksheet reconstruction, release gate or recomputation. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicScoreDetails(String schemaVersion, String validationStatus, String aggregationMode,
        BigDecimal audioScore, BigDecimal visualScore, Integer audioWeight, Integer visualWeight,
        List<Criterion> criteria) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Criterion(String criterionId, String label, BigDecimal score, String status,
            String reasonCode, Integer weight) {}
}
