package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.training.controller.dto.*;
import org.example.voice.training.domain.model.*;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainingCommandService {
    private final TrainingRequestReplayService replay;
    private final TrainingSessionService sessions;
    private final RecordingUploadService uploads;
    private final VoiceRecordingService recordings;
    private final TrainingAnalysisRequestService analyses;
    private final TrainingAnalysisSchemaAdmissionService admission;
    private final TrainingSessionWriter sessionWriter;
    private final VoiceRecordingReader recordingReader;

    public TrainingSessionCreatedData create(Long userId, TrainingSessionCreateRequestDto input, String key) {
        return replay.execute(userId, "CREATE_SESSION", key, () -> input, TrainingSessionCreatedData.class,
                () -> sessions.create(input, userId));
    }

    public RecordingUploadUrlData issueUpload(Long userId, Long sessionId, RecordingUploadUrlRequestDto input, String key) {
        if (key == null) return uploads.createUploadUrl(sessionId, input, userId);
        // Persist the immutable object reference, never a presigned credential.
        UploadReference reference = replay.execute(userId, "ISSUE_UPLOAD", key,
                () -> new Input(sessionId, input, null, null), UploadReference.class,
                () -> new UploadReference(uploads.createUploadUrl(sessionId, input, userId).objectKey()));
        return uploads.reissueUploadUrl(sessionId, reference.objectKey(), userId);
    }

    public VoiceRecordingRegisteredData register(Long userId, Long sessionId, RecordingRegisterRequestDto input, String key) {
        sessions.assertSessionExists(sessionId, userId);
        return replay.execute(userId, "REGISTER_RECORDING", key,
                () -> new Input(sessionId, input, null, null), VoiceRecordingRegisteredData.class,
                () -> recordings.register(sessionId, input, userId));
    }

    public AnalysisRequestData analyze(Long userId, Long sessionId, AnalysisConsentData input,
                                       AnalysisExecutionProfile profile, String key) {
        sessions.assertSessionExists(sessionId, userId);
        return replay.execute(userId, "REQUEST_ANALYSIS", key, () -> analysisInput(userId, sessionId, input, profile),
                AnalysisRequestData.class, () -> {
                    admission.assertAvailable(null);
                    return analyses.requestAnalysis(sessionId, userId, input, profile);
                });
    }

    public AnalysisRetryData retry(Long userId, Long sessionId, AnalysisConsentData input,
                                   AnalysisExecutionProfile profile, String key) {
        sessions.assertSessionExists(sessionId, userId);
        return replay.execute(userId, "RETRY_ANALYSIS", key, () -> analysisInput(userId, sessionId, input, profile),
                AnalysisRetryData.class, () -> {
                    admission.assertAvailable(null);
                    return analyses.retry(sessionId, userId, input, profile);
                });
    }

    private Input analysisInput(Long userId, Long sessionId, AnalysisConsentData input, AnalysisExecutionProfile profile) {
        sessionWriter.lockAnalysisSelection(sessionId, userId);
        return new Input(sessionId, input, profile, recordingReader.findSelectedRecordingId(sessionId, userId).orElse(null));
    }

    public record UploadReference(String objectKey) {}
    private record Input(Long sessionId, Object body, AnalysisExecutionProfile profile, Long recordingId) {}
}
