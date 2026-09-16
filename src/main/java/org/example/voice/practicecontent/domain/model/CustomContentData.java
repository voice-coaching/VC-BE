package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import java.text.BreakIterator;
import java.time.OffsetDateTime;
import java.util.*;

public record CustomContentData(Long id,String contentType,String origin,String title,String category,String difficulty,
        Integer estimatedSeconds,LearningFocus learningFocus,String description,String scriptText,
        List<String> targetPronunciations,boolean referenceAudioAvailable,List<Sentence> sentences,OffsetDateTime createdAt) {
    public record Sentence(int sequenceNo,String text,int startOffset,int endOffset) {}
    public static CustomContentData from(PracticeContent content) {
        String text=content.getScriptText();
        var iterator=BreakIterator.getSentenceInstance(Locale.KOREAN); iterator.setText(text);
        List<Sentence> parts=new ArrayList<>(); int begin=iterator.first();
        for(int end=iterator.next();end!=BreakIterator.DONE;begin=end,end=iterator.next()) {
            int start=begin, finish=end;
            while(start<finish && Character.isWhitespace(text.codePointAt(start))) start+=Character.charCount(text.codePointAt(start));
            while(finish>start && Character.isWhitespace(text.codePointBefore(finish))) finish-=Character.charCount(text.codePointBefore(finish));
            if(start<finish) parts.add(new Sentence(parts.size()+1,text.substring(start,finish),text.codePointCount(0,start),text.codePointCount(0,finish)));
        }
        return new CustomContentData(content.getId(),"SENTENCE","USER_INPUT",content.getTitle(),"CUSTOM","INTERMEDIATE",
                content.getEstimatedSeconds(),content.getLearningFocus(),content.getDescription(),text,List.of(),false,List.copyOf(parts),content.getCreatedAt());
    }
}
