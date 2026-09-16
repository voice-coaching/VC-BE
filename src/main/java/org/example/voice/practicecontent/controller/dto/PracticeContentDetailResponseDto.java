package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentDetailData;

import java.util.List;

public record PracticeContentDetailResponseDto(
        Long id,
        String contentType,
        String learningFocus,
        String category,
        String title,
        String description,
        String scriptText,
        String difficulty,
        List<String> targetPronunciations,
        Integer estimatedSeconds,
        Boolean referenceAudioAvailable,
        String origin,
        List<org.example.voice.practicecontent.domain.model.CustomContentData.Sentence> sentences,
        java.time.OffsetDateTime createdAt
) {

    public static PracticeContentDetailResponseDto from(PracticeContentDetailData data) {
        return new PracticeContentDetailResponseDto(
                data.id(),
                data.contentType().name(),
                data.learningFocus().name(),
                data.category(),
                data.title(),
                data.description(),
                data.scriptText(),
                data.difficulty().name(),
                data.targetPronunciations(),
                data.estimatedSeconds(),
                data.referenceAudioAvailable(), null, null, null
        );
    }

    public static PracticeContentDetailResponseDto fromCustom(org.example.voice.practicecontent.domain.model.CustomContentData data) {
        return new PracticeContentDetailResponseDto(data.id(),data.contentType(),data.learningFocus().name(),data.category(),
                data.title(),data.description(),data.scriptText(),data.difficulty(),data.targetPronunciations(),data.estimatedSeconds(),
                false,data.origin(),data.sentences(),data.createdAt());
    }
}
