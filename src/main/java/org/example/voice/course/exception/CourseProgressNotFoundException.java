package org.example.voice.course.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class CourseProgressNotFoundException extends BusinessException {

    public CourseProgressNotFoundException() {
        super(ErrorCode.COURSE_PROGRESS_NOT_FOUND);
    }
}
