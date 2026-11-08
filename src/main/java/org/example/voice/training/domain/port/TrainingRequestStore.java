package org.example.voice.training.domain.port;

import java.util.Optional;
import org.example.voice.training.domain.model.TrainingRequestReceipt;

public interface TrainingRequestStore {
    Optional<TrainingRequestReceipt> find(Long userId, String operation, String keySha256);
    void save(Long userId, String operation, String keySha256, TrainingRequestReceipt receipt);
}
