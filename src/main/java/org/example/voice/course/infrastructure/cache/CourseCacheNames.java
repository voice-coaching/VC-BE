package org.example.voice.course.infrastructure.cache;

public final class CourseCacheNames {

    private static final String GROUP = "course";

    public static final String LIST = GROUP + ":list";
    // Ignore pre-V53 entries that still include retired curriculum stages.
    public static final String DETAIL = GROUP + ":detail:v2";
    public static final String STEPS = GROUP + ":steps:v2";

    private CourseCacheNames() {
    }
}
