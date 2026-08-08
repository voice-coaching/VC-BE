package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.port.CourseProgressReader;
import org.example.voice.course.domain.port.CourseProgressWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseProgressService {

    private final CourseProgressReader courseProgressReader;
    private final CourseProgressWriter courseProgressWriter;
}
