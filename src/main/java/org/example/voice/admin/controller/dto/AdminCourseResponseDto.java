package org.example.voice.admin.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.admin.domain.model.AdminCourseData.*;
import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.type.*;

public record AdminCourseResponseDto(long id,int version,CourseType courseType,String title,String description,
        Difficulty difficulty,PublishStatus status,Integer estimatedMinutes,OffsetDateTime updatedAt,List<SentenceDto> sentences) {
    public record SentenceDto(Long contentId,Long contentVersion,String title,String text,String hint,LearningFocus focus,Integer estimatedSeconds) {}
    public static AdminCourseResponseDto from(View view) {
        return new AdminCourseResponseDto(view.id(),view.version(),view.courseType(),view.title(),view.description(),view.difficulty(),view.status(),view.estimatedMinutes(),view.updatedAt(),
                view.sentences().stream().map(s->new SentenceDto(s.contentId(),s.contentVersion(),s.title(),s.text(),s.hint(),s.focus(),s.estimatedSeconds())).toList());
    }
}
