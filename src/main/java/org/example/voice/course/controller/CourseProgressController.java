package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.application.CourseProgressService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseProgressController {

    private final CourseProgressService courseProgressService;
}
