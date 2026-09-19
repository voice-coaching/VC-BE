package org.example.voice.notification.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.domain.model.NotificationData.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id private Long userId;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "reminder_time", nullable = false, length = 5) private String time;
    @Column(name = "weekdays", nullable = false, length = 27) private String weekdays;
    @Column(nullable = false, length = 100) private String timezone;
    @Column(nullable = false) private OffsetDateTime updatedAt;

    public static NotificationPreference defaults(Long userId, OffsetDateTime createdAt) {
        var result = new NotificationPreference();
        result.userId = userId; result.time = "21:00";
        result.weekdays = "MON,TUE,WED,THU,FRI,SAT,SUN"; result.timezone = "Asia/Seoul";
        result.updatedAt = createdAt; return result;
    }
    public Preferences view() {
        return new Preferences(new Reminder(enabled, time, days(), timezone), new Marketing(false),
                updatedAt.withOffsetSameInstant(ZoneOffset.UTC));
    }
    private List<Weekday> days() {
        return weekdays.isEmpty() ? List.of() : Arrays.stream(weekdays.split(",")).map(Weekday::valueOf).toList();
    }
    public void patch(Patch patch, OffsetDateTime now) {
        if (patch == null || (patch.practiceReminder() == null && patch.marketing() == null)) throw NotificationException.invalid();
        if (patch.marketing() != null && patch.marketing().enabled())
            throw new NotificationException(409, "MARKETING_CONSENT_REQUIRED");
        var p = patch.practiceReminder();
        if (p != null) {
            boolean nextEnabled = p.enabled() == null ? enabled : p.enabled();
            String nextTime = p.time() == null ? time : p.time();
            String nextZone = p.timezone() == null ? timezone : p.timezone();
            List<Weekday> nextDays = p.daysOfWeek() == null ? days() : p.daysOfWeek();
            if (!nextTime.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") || nextZone.length() > 100
                    || !ZoneId.getAvailableZoneIds().contains(nextZone) || nextDays.size() > 7
                    || nextDays.stream().anyMatch(Objects::isNull) || new HashSet<>(nextDays).size() != nextDays.size()
                    || (nextEnabled && nextDays.isEmpty())) throw NotificationException.invalid();
            enabled = nextEnabled; time = nextTime; timezone = nextZone;
            weekdays = nextDays.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        }
        updatedAt = now;
    }
    public LocalDate dueDate(OffsetDateTime now) {
        if (!enabled) return null;
        var local = now.atZoneSameInstant(ZoneId.of(timezone));
        if (!days().contains(Weekday.values()[local.getDayOfWeek().getValue() - 1])) return null;
        var scheduled = local.toLocalDate().atTime(LocalTime.parse(time)).atZone(local.getZone());
        long seconds = Duration.between(scheduled.toInstant(), now.toInstant()).getSeconds();
        return seconds >= 0 && seconds < 300 ? local.toLocalDate() : null;
    }
}
