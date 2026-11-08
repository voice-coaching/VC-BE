package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.model.TrainingRequestReceipt;
import org.example.voice.training.domain.port.TrainingRequestStore;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/** The owner lock and receipt commit in the same transaction as the underlying command/outbox. */
@Service
@RequiredArgsConstructor
public class TrainingRequestReplayService {
    private final TrainingRequestStore store;
    private final UserReader users;
    private final ObjectMapper json;

    @Transactional
    public <T> T execute(Long userId, String operation, String key, Supplier<?> input,
                         Class<T> responseType, Supplier<T> command) {
        if (key == null) return command.get();
        if (!key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new BaseException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
        users.findByIdForUpdate(userId).orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        String keyHash = sha256(key);
        String requestHash = sha256(json.writeValueAsString(input.get()));
        var previous = store.find(userId, operation, keyHash);
        if (previous.isPresent()) {
            if (!previous.get().requestSha256().equals(requestHash))
                throw new BaseException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            return json.readValue(previous.get().responseJson(), responseType);
        }
        T response = command.get();
        store.save(userId, operation, keyHash,
                new TrainingRequestReceipt(requestHash, json.writeValueAsString(response)));
        return response;
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
