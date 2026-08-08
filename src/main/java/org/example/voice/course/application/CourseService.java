package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.CourseReader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseReader courseReader;
}
