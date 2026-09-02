package org.example.voice.training.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "storage", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PresignedUrlProvider implements RecordingObjectStoragePort {

    // 실제 스토리지 연동 전까지 objectKey 규칙만 먼저 고정한다.
    // S3/Cloudflare R2/NCP Object Storage를 붙여도 이 key를 그대로 파일 경로로 사용할 수 있다.
    public String createObjectKey(Long userId, Long sessionId, String fileName) {
        String extension = "";
        if (fileName != null && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf('.'));
        }
        return "recordings/users/%d/sessions/%d/%s%s".formatted(userId, sessionId, UUID.randomUUID(), extension);
    }

    // 개발용 upload URL이다.
    // 실제 Presigned URL 발급은 이 메서드 내부를 SDK 호출로 바꾸거나 별도 구현체로 분리하면 된다.
    public String createUploadUrl(
            String objectKey,
            String mimeType,
            long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {
        return "https://storage.example.com/%s?signature=dev-upload&expiresAt=%s"
                .formatted(objectKey, expiresAt);
    }

    // 개발용 playback URL이다.
    // DB에 이미 http URL이 저장된 테스트 데이터는 그대로 반환하고, objectKey만 있으면 임시 CDN URL로 감싼다.
    public String createPlaybackUrl(String objectKey, OffsetDateTime expiresAt) {
        if (objectKey != null && objectKey.startsWith("http")) {
            return objectKey;
        }
        return "https://storage.example.com/%s?signature=dev-playback&expiresAt=%s"
                .formatted(objectKey, expiresAt);
    }

    // Presigned PUT 요청에서 프론트가 함께 보내야 하는 헤더다.
    // 실제 스토리지 정책에 따라 Content-MD5, x-amz-* 같은 헤더가 추가될 수 있다.
    public Map<String, String> requiredHeaders(String mimeType, long fileSizeBytes) {
        return Map.of(
                "Content-Type", mimeType,
                "Content-Length", Long.toString(fileSizeBytes)
        );
    }

    @Override
    public void assertUploadedObject(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            long fileSizeBytes
    ) {
        // Development mode has no object store. Stream analysis is guarded from using this adapter.
        String ownerPrefix = "recordings/users/%d/sessions/%d/".formatted(userId, sessionId);
        if (objectKey == null || !objectKey.startsWith(ownerPrefix) || objectKey.length() <= ownerPrefix.length()) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
    }
}
