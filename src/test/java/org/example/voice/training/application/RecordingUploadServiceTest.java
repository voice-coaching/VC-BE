package org.example.voice.training.application;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.controller.dto.RecordingUploadUrlRequestDto;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordingUploadServiceTest {
    @Mock private RecordingObjectStoragePort objectStorage;
    @Mock private TrainingSessionService sessions;
    @Mock private TrainingSessionWriter sessionWriter;
    @Mock private RecordingUploadIntentRegistry uploadIntentRegistry;

    @Test
    void acceptsSupportedVideoWithinTheVideoLimit() {
        RecordingUploadUrlRequestDto request = new RecordingUploadUrlRequestDto(
                "attempt.mp4",
                "video/mp4",
                100L * 1024L * 1024L
        );
        String key = "recordings/users/9/sessions/7/source.mp4";
        when(objectStorage.createObjectKey(9L, 7L, request.fileName())).thenReturn(key);
        when(objectStorage.createUploadUrl(any(), any(), anyLong(), any(OffsetDateTime.class)))
                .thenReturn("https://storage.example.invalid/upload");
        when(objectStorage.requiredHeaders(request.mimeType(), request.fileSizeBytes()))
                .thenReturn(Map.of("Content-Type", "video/mp4"));

        var result = service().createUploadUrl(7L, request, 9L);

        assertThat(result.objectKey()).isEqualTo(key);
        verify(uploadIntentRegistry).recordIssued(
                9L,
                7L,
                key,
                request.mimeType(),
                request.fileSizeBytes(),
                result.expiresAt()
        );
    }

    @Test
    void rejectsVideoOverTheLimitAndUnknownContainers() {
        assertThatThrownBy(() -> service().createUploadUrl(
                7L,
                new RecordingUploadUrlRequestDto(
                        "attempt.mp4",
                        "video/mp4",
                        100L * 1024L * 1024L + 1
                ),
                9L
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUDIO_FILE_TOO_LARGE));

        assertThatThrownBy(() -> service().createUploadUrl(
                7L,
                new RecordingUploadUrlRequestDto("attempt.avi", "video/x-msvideo", 1_000L),
                9L
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_AUDIO_FORMAT));
    }

    private RecordingUploadService service() {
        return new RecordingUploadService(objectStorage, sessions, sessionWriter, uploadIntentRegistry);
    }
}
