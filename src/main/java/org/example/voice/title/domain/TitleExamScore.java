package org.example.voice.title.domain;

import java.math.BigDecimal;
import java.util.Optional;

/** One database snapshot distinguishes persistence in progress from a final missing score. */
public record TitleExamScore(boolean pending, Optional<BigDecimal> score) {
    public static TitleExamScore waiting() { return new TitleExamScore(true,Optional.empty()); }
    public static TitleExamScore unavailable() { return new TitleExamScore(false,Optional.empty()); }
    public static TitleExamScore ready(BigDecimal score) { return new TitleExamScore(false,Optional.of(score)); }
}
