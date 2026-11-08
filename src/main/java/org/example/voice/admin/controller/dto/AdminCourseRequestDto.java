package org.example.voice.admin.controller.dto;

import java.util.List;
import org.example.voice.admin.domain.model.AdminCourseData.*;
import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.type.*;

public record AdminCourseRequestDto(Integer version, CourseDraftDto course, String reason) {
    public record SentenceDto(Long contentId,Long contentVersion,String title,String text,String hint,LearningFocus focus,Integer estimatedSeconds) {
        Sentence toData(){return new Sentence(contentId,contentVersion,title,text,hint,focus,estimatedSeconds);}
    }
    public record CourseDraftDto(CourseType courseType,String title,String description,Difficulty difficulty,PublishStatus status,Integer estimatedMinutes,List<SentenceDto> sentences) {
        public Draft toData(){return new Draft(courseType,title,description,difficulty,status,estimatedMinutes,sentences==null?null:sentences.stream().map(s->s==null?null:s.toData()).toList());}
    }
}
