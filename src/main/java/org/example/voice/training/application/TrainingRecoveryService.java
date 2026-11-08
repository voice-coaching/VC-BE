package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.model.TrainingRecoveryData;
import org.example.voice.training.domain.port.*;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingRecoveryService {
    public static final int UPLOAD_LIMIT = 20;
    private final TrainingSessionService sessions;
    private final TrainingAnalysisReader analyses;
    private final RecordingUploadIntentRegistry intents;
    private final RecordingObjectStoragePort storage;
    private final VoiceRecordingReader recordings;
    private final Clock clock;

    public TrainingRecoveryData get(Long sessionId, Long userId) {
        sessions.assertSessionExists(sessionId, userId);
        var session = sessions.getSession(sessionId, userId);
        var now = OffsetDateTime.now(clock);
        var recent = intents.recent(userId, sessionId, UPLOAD_LIMIT + 1);
        var recordingIds = recent.stream().limit(UPLOAD_LIMIT).map(i -> i.recordingId())
                .filter(java.util.Objects::nonNull).toList();
        var availableRecordings = recordings.findAvailableIds(sessionId, userId, recordingIds);
        var uploads = recent.stream().limit(UPLOAD_LIMIT).map(intent -> {
            String state;
            if (intent.status() == RecordingUploadIntentStatus.CONSUMED) {
                state = intent.recordingId() != null && availableRecordings.contains(intent.recordingId())
                        ? "REGISTERED" : "RECORDING_UNAVAILABLE";
            } else if (intent.status() == RecordingUploadIntentStatus.EXPIRED || !intent.expiresAt().isAfter(now)) {
                state = "EXPIRED";
            } else {
                state = storage.isUploadedObject(userId, sessionId, intent.objectKey(), intent.mimeType(), intent.fileSizeBytes())
                        ? "READY_TO_REGISTER" : "UPLOAD_REQUIRED";
            }
            return new TrainingRecoveryData.Upload(intent.id(), intent.objectKey(), intent.mimeType(), intent.fileSizeBytes(),
                    state, intent.recordingId(), intent.expiresAt());
        }).toList();
        return new TrainingRecoveryData(sessionId, session.status().name(), session.selectedRecordingId(),
                analyses.findLatestBySelectedRecording(sessionId, userId).orElse(null), uploads,
                recent.size() > UPLOAD_LIMIT, now);
    }
}
