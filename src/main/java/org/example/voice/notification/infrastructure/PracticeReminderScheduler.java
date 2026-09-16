package org.example.voice.notification.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.notification.application.NotificationService;
import org.example.voice.notification.domain.port.NotificationStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "notification.reminders.enabled", havingValue = "true")
public class PracticeReminderScheduler {
    private final NotificationStore store;
    private final NotificationService service;
    @Scheduled(fixedDelayString = "${notification.reminders.poll-interval:PT1M}")
    public void generate() {
        long after = 0;
        while (true) {
            var users = store.reminderUsers(after, 100);
            if (users.isEmpty()) return;
            for (Long userId : users) {
                try { service.generateReminder(userId); }
                catch (RuntimeException e) { log.warn("Practice reminder generation failed; will retry on next poll"); }
            }
            after = users.getLast();
        }
    }
}
