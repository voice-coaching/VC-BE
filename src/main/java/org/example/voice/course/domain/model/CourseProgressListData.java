package org.example.voice.course.domain.model;

import java.util.List;

public record CourseProgressListData(
        List<CourseProgressItemData> items
) {
}
