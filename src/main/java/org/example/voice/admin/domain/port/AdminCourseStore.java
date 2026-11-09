package org.example.voice.admin.domain.port;

import java.time.OffsetDateTime;
import org.example.voice.admin.domain.model.AdminCourseData.*;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.course.domain.entity.Course;

public interface AdminCourseStore {
    Page<View> list(Query query);
    View read(long id);
    Course lock(long id);
    Course create(Draft draft, OffsetDateTime now);
    View replace(Course course, Draft draft, OffsetDateTime now);
}
