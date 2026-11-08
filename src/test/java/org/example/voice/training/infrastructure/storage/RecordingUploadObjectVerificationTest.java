package org.example.voice.training.infrastructure.storage;

import org.example.voice.common.exception.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class RecordingUploadObjectVerificationTest {
    final S3Client s3 = mock(S3Client.class);
    final ObjectStorageProperties config = new ObjectStorageProperties();
    final S3PresignedUrlProvider storage = new S3PresignedUrlProvider(config, s3, mock(S3Presigner.class));
    final String key = "recordings/users/9/sessions/7/fixture.wav";

    @Test void missingObjectMeansUploadRequiredAndCannotBeRegistered() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(storage.isUploadedObject(9L, 7L, key, "audio/wav", 123L)).isFalse();
        assertThatThrownBy(() -> storage.assertUploadedObject(9L, 7L, key, "audio/wav", 123L))
                .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPLOADED_OBJECT_NOT_FOUND));
    }

    @Test void realMetadataMustMatchBeforeRegistration() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(123L).contentType("audio/wav").build());
        assertThat(storage.isUploadedObject(9L, 7L, key, "audio/wav", 123L)).isTrue();
        assertThatThrownBy(() -> storage.assertUploadedObject(9L, 7L, key, "audio/wav", 124L))
                .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_SOURCE_NOT_READY));
        assertThatThrownBy(() -> storage.assertUploadedObject(9L, 7L, key, "video/mp4", 123L))
                .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_SOURCE_NOT_READY));
    }

    @Test void forbiddenOrUnavailableStorageIsNeverReportedAsMissing() {
        for (int status : new int[]{403, 500, 503}) {
            when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(status).build());
            assertThatThrownBy(() -> storage.isUploadedObject(9L, 7L, key, "audio/wav", 123L))
                    .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE));
        }
    }

    @Test void anotherOwnerObjectIsRejectedBeforeAccessingStorage() {
        assertThatThrownBy(() -> storage.isUploadedObject(10L, 7L, key, "audio/wav", 123L))
                .isInstanceOfSatisfying(BaseException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));
        verifyNoInteractions(s3);
    }

    @Test void normalizedMediaDeletionRetainsTheCurrentNormalizerKeyFormat() {
        for (String suffix : new String[]{"analysis-audio/4adfe173-0691-4e89-b94e-a5c5c5085826.wav",
                "analysis-video/4adfe173-0691-4e89-b94e-a5c5c5085826.mp4"}) {
            String objectKey = "recordings/" + suffix;
            storage.deleteObject(9L, 7L, objectKey);
            verify(s3).deleteObject(argThat((DeleteObjectRequest request) -> request.key().equals(objectKey)));
        }
    }
}
