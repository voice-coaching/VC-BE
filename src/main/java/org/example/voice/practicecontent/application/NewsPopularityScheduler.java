package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsPopularityScheduler {
    private final NewsPopularityService service;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron="0 0 0 * * *", zone="Asia/Seoul")
    public void refresh() {
        try {
            service.get(ContentType.NEWS, 10);
        } catch (RuntimeException error) {
            // A later authenticated read can retry an absent snapshot; partial builds roll back.
            log.warn("Daily news popularity refresh failed", error);
        }
    }
}
