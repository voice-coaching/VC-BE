package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.controller.dto.RecordingUploadUrlRequestDto;
import org.example.voice.training.domain.model.RecordingUploadUrlData;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class RecordingUploadService {

    // 업로드 URL 발급 정책만 담당한다.
    // 실제 파일 저장은 프론트가 uploadUrl로 직접 업로드하고, 완료 후 VoiceRecordingService에 metadata를 등록한다.
    private static final long MAX_AUDIO_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_VIDEO_FILE_SIZE_BYTES = 100L * 1024L * 1024L;

    private final RecordingObjectStoragePort objectStorage;
    private final TrainingSessionService trainingSessionService;
    private final TrainingSessionWriter trainingSessionWriter;

    public RecordingUploadUrlData createUploadUrl(Long sessionId, RecordingUploadUrlRequestDto request, Long userId) {
        trainingSessionService.assertSessionExists(sessionId, userId);
        validate(request);
        trainingSessionWriter.beginUpload(sessionId);

        // 현재는 개발용 URL을 반환하지만, objectKey/expiresAt/requiredHeaders 구조는 실제 Presigned URL과 동일하게 맞춰뒀다.
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(10);
        String objectKey = objectStorage.createObjectKey(userId, sessionId, request.fileName());
        return new RecordingUploadUrlData(
                objectKey,
                objectStorage.createUploadUrl(
                        objectKey, request.mimeType(), request.fileSizeBytes(), expiresAt
                ),
                expiresAt,
                objectStorage.requiredHeaders(request.mimeType(), request.fileSizeBytes())
        );
    }

    private void validate(RecordingUploadUrlRequestDto request) {
        if (request == null
                || request.fileName() == null
                || request.fileName().isBlank()
                || request.mimeType() == null
                || request.fileSizeBytes() == null
                || request.fileSizeBytes() <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!isSupportedAudio(request.mimeType()) && !isSupportedVideo(request.mimeType())) {
            throw new BaseException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT);
        }
        long maximumBytes = isSupportedVideo(request.mimeType())
                ? MAX_VIDEO_FILE_SIZE_BYTES
                : MAX_AUDIO_FILE_SIZE_BYTES;
        if (request.fileSizeBytes() > maximumBytes) {
            throw new BaseException(ErrorCode.AUDIO_FILE_TOO_LARGE);
        }
    }

    private static boolean isSupportedAudio(String mimeType) {
        return mimeType.equals("audio/webm")
                || mimeType.equals("audio/mpeg")
                || mimeType.equals("audio/wav");
    }

    private static boolean isSupportedVideo(String mimeType) {
        return mimeType.equals("video/mp4")
                || mimeType.equals("video/quicktime")
                || mimeType.equals("video/webm");
    }
}
