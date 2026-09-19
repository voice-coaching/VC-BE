package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.PronunciationEvidenceData;

import java.math.BigDecimal;

public record PronunciationEvidenceResponseDto(
        String schemaVersion,
        String selectedPhone,
        Integer selectedExpectedIndex,
        Integer selectedStartMs,
        Integer selectedEndMs,
        BigDecimal detectorScore,
        BigDecimal operatingThreshold,
        String scoreSemantics,
        String evidenceState
) {
    public static PronunciationEvidenceResponseDto from(PronunciationEvidenceData data) {
        if (data == null) {
            return null;
        }
        return new PronunciationEvidenceResponseDto(
                data.schemaVersion(),
                data.selectedPhone(),
                data.selectedExpectedIndex(),
                data.selectedStartMs(),
                data.selectedEndMs(),
                data.detectorScore(),
                data.operatingThreshold(),
                data.scoreSemantics(),
                data.evidenceState()
        );
    }
}
