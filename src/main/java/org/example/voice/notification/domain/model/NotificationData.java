package org.example.voice.notification.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public final class NotificationData {
    private NotificationData() {}
    public enum Weekday { MON, TUE, WED, THU, FRI, SAT, SUN }
    public record Reminder(boolean enabled, String time, List<Weekday> daysOfWeek, String timezone) {}
    public record Marketing(boolean enabled) {}
    public record Preferences(Reminder practiceReminder, Marketing marketing, OffsetDateTime updatedAt) {}
    public record ReminderPatch(Boolean enabled, String time, List<Weekday> daysOfWeek, String timezone) {}
    public record Patch(ReminderPatch practiceReminder, Marketing marketing) {}
    public record PushDraft(String endpoint, String p256dh, String auth, String userAgent, String deviceName) {
        @Override public String toString() { return "PushDraft[redacted]"; }
    }
    public record Subscription(Long id, String deviceName, boolean active, OffsetDateTime createdAt) {}
    public record Item(Long id, String type, String title, String body, String deepLink,
                       OffsetDateTime readAt, OffsetDateTime createdAt) {}
    public record Page(List<Item> items, int page, int size, long totalElements, int totalPages,
                       boolean hasNext, long unreadCount) {}
    public record ReadAll(int updatedCount) {}
}
