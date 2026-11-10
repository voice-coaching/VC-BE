package org.example.voice.admin.infrastructure;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PopupImageScheduling {
    @Bean
    public ThreadPoolTaskScheduler popupImageCleanupScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("popup-image-cleanup-");scheduler.setDaemon(true);return scheduler;
    }
}
