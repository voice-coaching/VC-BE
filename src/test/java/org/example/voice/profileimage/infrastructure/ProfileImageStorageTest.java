package org.example.voice.profileimage.infrastructure;

import org.example.voice.profileimage.domain.ProfileImageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileImageStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final String key = "profiles/00000000-0000-0000-0000-000000000001.png";
    @Test void uploadsEncryptedPngAndUsesStableUrlAndOwnedPrefix() throws Exception {
        var storage = new ProfileImageStorageConfiguration.S3ProfileImageStorage(client, "photo-test", "https://cdn.example.invalid");
        storage.upload(key, new byte[]{1,2});
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(storage.imageUrl(key)).isEqualTo("https://cdn.example.invalid/" + key);
        try (var bytes = body.getValue().contentStreamProvider().newStream()) { assertThat(bytes.readAllBytes()).containsExactly(1,2); }
        assertThatThrownBy(() -> storage.delete("recordings/other.wav")).isInstanceOf(IllegalArgumentException.class);
        storage.delete(key); verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
    @Test void masksProviderErrorsAndRejectsCredentialBearingCdnUrls() {
        var storage = new ProfileImageStorageConfiguration.S3ProfileImageStorage(client, "photo-test", "https://cdn.example.invalid");
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new RuntimeException("provider secret"));
        assertThatThrownBy(() -> storage.upload(key, new byte[]{1})).isInstanceOf(ProfileImageException.class).hasMessage("TEMPORARY_UNAVAILABLE");
        for (String url : new String[]{"http://cdn.example.invalid", "https://user:password@cdn.example.invalid", "https://cdn.example.invalid?token=secret"}) {
            assertThatThrownBy(() -> ProfileImageStorageConfiguration.validateCdn(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
