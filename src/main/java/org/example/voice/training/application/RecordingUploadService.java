package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.controller.dto.RecordingUploadUrlRequestDto;
import org.example.voice.training.domain.model.RecordingUploadUrlData;
import org.example.voice.training.domain.model.RecordingMediaPolicy;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RecordingUploadService {

    // 업로드 URL 발급 정책만 담당한다.
    // 실제 파일 저장은 프론트가 uploadUrl로 직접 업로드하고, 완료 후 VoiceRecordingService에 metadata를 등록한다.
    private final RecordingObjectStoragePort objectStorage;
    private final TrainingSessionService trainingSessionService;
    private final TrainingSessionWriter trainingSessionWriter;
    private final RecordingUploadIntentRegistry uploadIntentRegistry;
    private final Clock clock;

    @Transactional
    public RecordingUploadUrlData createUploadUrl(Long sessionId, RecordingUploadUrlRequestDto request, Long userId) {
        trainingSessionService.assertSessionExists(sessionId, userId);
        if (request == null || request.fileName() == null || request.fileName().isBlank()
                || request.mimeType() == null || request.fileSizeBytes() == null || request.fileSizeBytes() <= 0)
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        if (!isSupportedAudio(request.mimeType()) && !isSupportedVideo(request.mimeType()))
            throw new BaseException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT);
        trainingSessionWriter.beginUpload(sessionId);

        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusMinutes(10);
        String objectKey = objectStorage.createObjectKey(userId, sessionId, request.fileName());
        uploadIntentRegistry.recordIssued(
                userId,
                sessionId,
                objectKey,
                request.mimeType(),
                request.fileSizeBytes(),
                expiresAt
        );
        return new RecordingUploadUrlData(
                objectKey,
                objectStorage.createUploadUrl(
                        objectKey, request.mimeType(), request.fileSizeBytes(), expiresAt
                ),
                expiresAt,
                objectStorage.requiredHeaders(request.mimeType(), request.fileSizeBytes())
        );
    }

    @Transactional
    public RecordingUploadUrlData reissueUploadUrl(Long sessionId, String objectKey, Long userId) {
        trainingSessionService.assertSessionExists(sessionId, userId);
        var intent = uploadIntentRegistry.find(userId, sessionId, objectKey)
                .orElseThrow(() -> new BaseException(ErrorCode.UPLOAD_INTENT_NOT_FOUND));
        if (intent.status() != org.example.voice.training.domain.type.RecordingUploadIntentStatus.ISSUED
                || !intent.expiresAt().isAfter(OffsetDateTime.now(clock)))
            throw new BaseException(ErrorCode.UPLOAD_INTENT_NOT_ACTIVE);
        return new RecordingUploadUrlData(objectKey,
                objectStorage.createUploadUrl(objectKey, intent.mimeType(), intent.fileSizeBytes(), intent.expiresAt()),
                intent.expiresAt(), objectStorage.requiredHeaders(intent.mimeType(), intent.fileSizeBytes()));
    }

    private static boolean isSupportedAudio(String mimeType) {
        return RecordingMediaPolicy.AUDIO_MIME_TYPES.contains(mimeType);
    }

    private static boolean isSupportedVideo(String mimeType) {
        return RecordingMediaPolicy.VIDEO_MIME_TYPES.contains(mimeType);
    }
}
