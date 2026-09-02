package org.example.voice.training.application;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.training.controller.dto.RecordingRegisterRequestDto;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.VoiceRecordingRegisteredData;
import org.example.voice.training.domain.port.RecordingMediaNormalizationPort;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.port.VoiceRecordingWriter;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceRecordingServiceTest {
    @Mock private VoiceRecordingReader reader;
    @Mock private VoiceRecordingWriter writer;
    @Mock private TrainingSessionService sessions;
    @Mock private RecordingObjectStoragePort objectStorage;
    @Mock private RecordingMediaNormalizationPort normalization;
    @Mock private ProcessingConsentLedger consentLedger;
    @Mock private RecordingUploadIntentRegistry uploadIntentRegistry;

    @Test
    void storesOnlyBackendNormalizedMediaAndMeasuredQuality() {
        RecordingRegisterRequestDto request = audioRequest();
        NormalizedRecordingData normalized = normalized();
        when(normalization.normalize(9L, 7L, request.objectKey(), request.mimeType(), request.fileSizeBytes()))
                .thenReturn(normalized);
        when(writer.register(7L, normalized)).thenReturn(
                new VoiceRecordingRegisteredData(
                        50L,
                        1,
                        RecordingQualityStatus.PASS,
                        false,
                        OffsetDateTime.now()
                )
        );

        VoiceRecordingRegisteredData result = service().register(7L, request, 9L);

        assertThat(result.recordingId()).isEqualTo(50L);
        verify(objectStorage).assertUploadedObject(
                9L, 7L, request.objectKey(), request.mimeType(), request.fileSizeBytes()
        );
        verify(uploadIntentRegistry).markConsumed(9L, 7L, request.objectKey());
        verify(writer).register(7L, normalized);
    }

    @Test
    void refusesVideoBeforeDecodeWithoutExactFaceVideoConsent() {
        RecordingRegisterRequestDto request = new RecordingRegisterRequestDto(
                "recordings/users/9/sessions/7/source.mp4",
                "video/mp4",
                2_000L,
                1_000,
                false,
                "voice-video-processing-consent-v1"
        );

        assertThatThrownBy(() -> service().register(7L, request, 9L))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.VIDEO_PROCESSING_CONSENT_REQUIRED));

        verify(objectStorage, never()).assertUploadedObject(any(), any(), any(), any(), anyLong());
        verify(normalization, never()).normalize(any(), any(), any(), any(), anyLong());
        verify(consentLedger, never()).grantFaceVideoProcessing(any(), any(), any(), any());
    }

    @Test
    void recordsVideoConsentBeforeNormalizingOwnedMedia() {
        RecordingRegisterRequestDto request = new RecordingRegisterRequestDto(
                "recordings/users/9/sessions/7/source.mp4",
                "video/mp4",
                2_000L,
                1_000,
                true,
                "voice-video-processing-consent-v1"
        );
        NormalizedRecordingData normalized = normalized();
        when(normalization.normalize(9L, 7L, request.objectKey(), request.mimeType(), request.fileSizeBytes()))
                .thenReturn(normalized);
        when(writer.register(7L, normalized)).thenReturn(
                new VoiceRecordingRegisteredData(
                        50L,
                        1,
                        RecordingQualityStatus.PASS,
                        false,
                        OffsetDateTime.now()
                )
        );

        service().register(7L, request, 9L);

        verify(consentLedger).grantFaceVideoProcessing(
                9L,
                7L,
                "voice-video-processing-consent-v1",
                request.objectKey()
        );
        var inOrder = org.mockito.Mockito.inOrder(consentLedger, normalization);
        inOrder.verify(consentLedger).grantFaceVideoProcessing(any(), any(), any(), any());
        inOrder.verify(normalization).normalize(any(), any(), any(), any(), anyLong());
    }

    @Test
    void deletesNormalizedObjectWhenDatabaseRegistrationFails() {
        RecordingRegisterRequestDto request = audioRequest();
        NormalizedRecordingData normalized = normalized();
        when(normalization.normalize(9L, 7L, request.objectKey(), request.mimeType(), request.fileSizeBytes()))
                .thenReturn(normalized);
        when(writer.register(7L, normalized)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service().register(7L, request, 9L))
                .isInstanceOf(IllegalStateException.class);

        verify(normalization).deleteNormalizedObject(9L, 7L, normalized.objectKey());
    }

    private VoiceRecordingService service() {
        return new VoiceRecordingService(
                reader,
                writer,
                sessions,
                objectStorage,
                normalization,
                consentLedger,
                uploadIntentRegistry
        );
    }

    private static RecordingRegisterRequestDto audioRequest() {
        return new RecordingRegisterRequestDto(
                "recordings/users/9/sessions/7/source.webm",
                "audio/webm",
                2_000L,
                1_000,
                null,
                null
        );
    }

    private static NormalizedRecordingData normalized() {
        return new NormalizedRecordingData(
                "recordings/users/9/sessions/7/normalized/canonical.wav",
                "audio/wav",
                32_044L,
                1_000,
                "a".repeat(64),
                RecordingQualityStatus.PASS,
                BigDecimal.valueOf(75),
                null
        );
    }
}
