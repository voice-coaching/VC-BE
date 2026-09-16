package org.example.voice.profileimage.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.profileimage.application.ProfileImageTransactions;
import org.example.voice.profileimage.domain.port.ProfileImageStorage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImageCleanup {
    private final ProfileImageTransactions transactions;
    private final ProfileImageStorage storage;
    @Scheduled(fixedDelayString = "${profile-image.cleanup-delay-ms:60000}", initialDelayString = "${profile-image.cleanup-delay-ms:60000}")
    public void clean() {
        for (Long id : transactions.cleanupDue()) {
            try {
                String key = transactions.claimCleanup(id);
                if (key != null) { storage.delete(key); transactions.cleaned(id); }
            } catch (RuntimeException ignored) {
                // The durable row remains eligible for retry. Never log storage paths or file names.
            }
        }
    }
}
