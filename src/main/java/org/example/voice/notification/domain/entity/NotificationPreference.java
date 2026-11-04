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
    @Column(nullable = true) private boolean enabled;
    @Column(name = "reminder_time", nullable = true, length = 5) private String time;
    @Column(name = "weekdays", nullable = true, length = 27) private String weekdays;
    @Column(nullable = true, length = 100) private String timezone;
    @Column(nullable = true) private OffsetDateTime updatedAt;

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

        var p = patch.practiceReminder();
        if (p != null) {
            boolean nextEnabled = p.enabled() == null ? enabled : p.enabled();
            String nextTime = p.time() == null ? time : p.time();
            String nextZone = p.timezone() == null ? timezone : p.timezone();
            List<Weekday> nextDays = p.daysOfWeek() == null ? days() : p.daysOfWeek();

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
