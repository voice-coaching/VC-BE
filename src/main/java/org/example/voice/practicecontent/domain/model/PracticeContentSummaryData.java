package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record PracticeContentSummaryData(
        Long id,
        ContentType contentType,
        String title,
        String category,
        Difficulty difficulty,
        Integer estimatedSeconds,
        String scriptText,
        String publisher,
        Integer paragraphCount,
        Integer sentenceCount,
        Integer syllableCount,
        java.time.OffsetDateTime publishedAt,
        String speakerName
) {
    public PracticeContentSummaryData(Long id, ContentType contentType,String title,String category,Difficulty difficulty,Integer estimatedSeconds,String scriptText) {
        this(id,contentType,title,category,difficulty,estimatedSeconds,scriptText,null,null,null,null,null,null);
    }
}
