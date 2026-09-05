package org.example.voice.training.infrastructure.storage;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class S3PresignedUrlProviderTest {
    @Mock private S3Client s3Client;
    @Mock private S3Presigner presigner;

    @Test
    void verifiesUploadedObjectMetadataInPrivateBucket() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength(1234L).contentType("audio/wav").build()
        );
        S3PresignedUrlProvider provider = provider();

        assertThatCode(() -> provider.assertUploadedObject(
                9L, 7L, "recordings/users/9/sessions/7/attempt.wav", "audio/wav", 1234L
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsMetadataMismatchAndOutOfPrefixKey() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength(12L).contentType("audio/wav").build()
        );
        S3PresignedUrlProvider provider = provider();

        assertThatThrownBy(() -> provider.assertUploadedObject(
                9L, 7L, "recordings/users/9/sessions/7/attempt.wav", "audio/wav", 1234L
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_SOURCE_NOT_READY));
        assertThatThrownBy(() -> provider.assertUploadedObject(
                9L, 7L, "private/other.wav", "audio/wav", 1234L
        )).isInstanceOf(BaseException.class);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void rejectsAnotherUsersOrSessionsObjectBeforeCallingS3() {
        S3PresignedUrlProvider provider = provider();

        assertThatThrownBy(() -> provider.assertUploadedObject(
                9L, 7L, "recordings/users/10/sessions/7/attempt.wav", "audio/wav", 1234L
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));
        assertThatThrownBy(() -> provider.assertUploadedObject(
                9L, 7L, "recordings/users/9/sessions/8/attempt.wav", "audio/wav", 1234L
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));
        assertThatThrownBy(() -> provider.assertUploadedObject(
                9L, 7L, "recordings/users/9/sessions/7/normalized/attempt.wav", "audio/wav", 1234L
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));

        verify(s3Client, org.mockito.Mockito.never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void configurationRejectsNonTlsCustomEndpoint() {
        ObjectStorageProperties properties = properties();
        properties.setEndpoint("http://storage.internal");

        assertThatThrownBy(() -> S3ObjectStorageConfiguration.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("object_storage_configuration_invalid");
    }

    @Test
    void deletesOnlyAnOwnedCanonicalRecordingKey() {
        S3PresignedUrlProvider provider = provider();
        String key = "recordings/users/9/sessions/7/normalized/4adfe173-0691-4e89-b94e-a5c5c5085826.wav";

        provider.deleteObject(9L, 7L, key);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("private-recordings");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThatThrownBy(() -> provider.deleteObject(10L, 7L, key))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));
    }

    private S3PresignedUrlProvider provider() {
        return new S3PresignedUrlProvider(properties(), s3Client, presigner);
    }

    private static ObjectStorageProperties properties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEnabled(true);
        properties.setBucket("private-recordings");
        properties.setRegion("ap-northeast-2");
        properties.setRecordingsPrefix("recordings/");
        return properties;
    }
}
