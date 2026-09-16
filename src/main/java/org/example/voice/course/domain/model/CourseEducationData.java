package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseStepType;
import java.util.List;

public record CourseEducationData(Long id, Long courseId, Integer stepOrder, CourseStepType stepType,
                                  String title, String subtitle, Integer contentRevision,
                                  List<CourseBlock> blocks, boolean completed) {}
