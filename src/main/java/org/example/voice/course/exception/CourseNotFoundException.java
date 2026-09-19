package org.example.voice.course.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class CourseNotFoundException extends BusinessException {

    public CourseNotFoundException() {
        super(ErrorCode.COURSE_NOT_FOUND);
    }
}
