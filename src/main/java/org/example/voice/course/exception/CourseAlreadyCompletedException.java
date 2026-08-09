package org.example.voice.course.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class CourseAlreadyCompletedException extends BusinessException {

    public CourseAlreadyCompletedException() {
        super(ErrorCode.COURSE_ALREADY_COMPLETED);
    }
}
