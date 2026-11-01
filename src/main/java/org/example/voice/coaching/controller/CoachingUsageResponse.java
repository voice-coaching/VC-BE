package org.example.voice.coaching.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.voice.coaching.domain.CoachingUsage;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CoachingUsageResponse(String membershipType, @Schema(nullable=true) String environment,
        boolean unlimited, @Schema(nullable=true) OffsetDateTime startsAt,
        @Schema(nullable=true) OffsetDateTime expiresAt, @Schema(nullable=true) Integer limit,
        long used, long reserved, @Schema(nullable=true) Long remaining, boolean canRequestCoaching) {
    public static CoachingUsageResponse from(CoachingUsage usage) {
        return new CoachingUsageResponse(usage.membershipType(),usage.environment(),usage.unlimited(),
                usage.startsAt(),usage.expiresAt(),usage.limit(),usage.used(),usage.reserved(),
                usage.remaining(),usage.canRequestCoaching());
    }
}
