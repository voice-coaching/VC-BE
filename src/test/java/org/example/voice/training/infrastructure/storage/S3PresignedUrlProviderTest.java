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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
