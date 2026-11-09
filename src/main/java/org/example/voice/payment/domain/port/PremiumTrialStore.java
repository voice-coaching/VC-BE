package org.example.voice.payment.domain.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.example.voice.payment.domain.model.PremiumTrial;

public interface PremiumTrialStore {
    Optional<PremiumTrial> find(Long userId);
    Map<Long, PremiumTrial> findAll(Collection<Long> userIds);
    void insert(PremiumTrial trial);
}
