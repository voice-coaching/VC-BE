package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.controller.dto.RecordingUploadUrlRequestDto;
import org.example.voice.training.domain.model.RecordingUploadUrlData;
import org.example.voice.training.infrastructure.PresignedUrlProvider;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class RecordingUploadService {

    // 업로드 URL 발급 정책만 담당한다.
    // 실제 파일 저장은 프론트가 uploadUrl로 직접 업로드하고, 완료 후 VoiceRecordingService에 metadata를 등록한다.
    private static final long MAX_AUDIO_FILE_SIZE_BYTES = 20L * 1024L * 1024L;

    private final PresignedUrlProvider presignedUrlProvider;
    private final TrainingSessionService trainingSessionService;

    public RecordingUploadUrlData createUploadUrl(Long sessionId, RecordingUploadUrlRequestDto request, Long userId) {
        trainingSessionService.assertSessionExists(sessionId, userId);
        validate(request);

        // 현재는 개발용 URL을 반환하지만, objectKey/expiresAt/requiredHeaders 구조는 실제 Presigned URL과 동일하게 맞춰뒀다.
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(10);
        String objectKey = presignedUrlProvider.createObjectKey(userId, sessionId, request.fileName());
        return new RecordingUploadUrlData(
                objectKey,
                presignedUrlProvider.createUploadUrl(objectKey, expiresAt),
                expiresAt,
                presignedUrlProvider.requiredHeaders(request.mimeType())
        );
    }

    private void validate(RecordingUploadUrlRequestDto request) {
        if (request == null || request.fileName() == null || request.mimeType() == null || request.fileSizeBytes() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!request.mimeType().equals("audio/webm") && !request.mimeType().equals("audio/mpeg") && !request.mimeType().equals("audio/wav")) {
            throw new BaseException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT);
        }
        if (request.fileSizeBytes() > MAX_AUDIO_FILE_SIZE_BYTES) {
            throw new BaseException(ErrorCode.AUDIO_FILE_TOO_LARGE);
        }
    }
}
