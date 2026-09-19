package org.example.voice.course.domain;

public class CourseEducationException extends RuntimeException {
    private final int status;
    public CourseEducationException(int status, String code) { super(code); this.status = status; }
    public int status() { return status; }
}
