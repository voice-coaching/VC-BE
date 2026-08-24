package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.AnalysisSegmentData;
import org.example.voice.analysis.domain.model.AnalysisSegmentPageData;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisSegmentResponseDto(List<Item> items, int page, int size, long totalElements) {
    public static AnalysisSegmentResponseDto from(AnalysisSegmentPageData result) {
        return new AnalysisSegmentResponseDto(result.items().stream().map(Item::from).toList(), result.page(), result.size(), result.totalElements());
    }
    public record Item(Long id, Integer sequenceNo, String expectedText, String recognizedText, Integer startMs,
            Integer endMs, String matchType, String resultStatus, String targetUnit, String errorType,
            BigDecimal pronunciationScore, BigDecimal intonationScore, String feedback) {
        static Item from(AnalysisSegmentData data) {
            return new Item(
                    data.id(),
                    data.sequenceNo(),
                    data.expectedText(),
                    data.recognizedText(),
                    data.startMs(),
                    data.endMs(),
                    data.matchType().name(),
                    data.resultStatus().name(),
                    data.targetUnit(),
                    data.errorType(),
                    data.pronunciationScore(),
                    data.intonationScore(),
                    data.feedback()
            );
        }
    }
}
