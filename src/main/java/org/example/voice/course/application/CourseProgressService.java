package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.CourseProgressReader;
import org.example.voice.course.domain.CourseProgressWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseProgressService {

    private final CourseProgressReader courseProgressReader;
    private final CourseProgressWriter courseProgressWriter;
}
