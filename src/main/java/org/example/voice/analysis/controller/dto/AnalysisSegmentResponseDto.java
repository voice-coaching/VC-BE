package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.application.AnalysisSegmentService;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import java.math.BigDecimal;
import java.util.List;

public record AnalysisSegmentResponseDto(List<Item> items, int page, int size, long totalElements) {
    public static AnalysisSegmentResponseDto from(AnalysisSegmentService.SegmentPage result) {
        return new AnalysisSegmentResponseDto(result.items().stream().map(Item::from).toList(), result.page(), result.size(), result.totalElements());
    }
    public record Item(Long id, Integer sequenceNo, String expectedText, String recognizedText, Integer startMs,
            Integer endMs, String matchType, String resultStatus, String targetUnit, String errorType,
            BigDecimal pronunciationScore, BigDecimal intonationScore, String feedback) {
        static Item from(AnalysisSegment s) {
            return new Item(s.getId(), s.getSequenceNo(), s.getExpectedText(), s.getRecognizedText(), s.getStartMs(), s.getEndMs(), s.getMatchType().name(), s.getResultStatus().name(), s.getTargetUnit(), s.getErrorType(), s.getPronunciationScore(), s.getIntonationScore(), s.getFeedback());
        }
    }
}
