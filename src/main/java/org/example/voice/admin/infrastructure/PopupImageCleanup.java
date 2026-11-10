package org.example.voice.admin.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.admin.application.PopupImageTransactions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class PopupImageCleanup {
    private final PopupImageTransactions transactions;
    @Scheduled(scheduler="popupImageCleanupScheduler",initialDelayString="${popup-image.cleanup-delay-ms:60000}",fixedDelayString="${popup-image.cleanup-delay-ms:60000}")
    public void run() {
        try {
            for(var id:transactions.due()) {
                try { transactions.cleanup(id); }
                catch(RuntimeException e){log.warn("Popup image cleanup deferred image_id={} error_type={}",id,e.getClass().getSimpleName());}
            }
        } catch(RuntimeException e){log.warn("Popup image cleanup unavailable error_type={}",e.getClass().getSimpleName());}
    }
}
