package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.CourseStepReader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseStepService {

    private final CourseStepReader courseStepReader;
}
