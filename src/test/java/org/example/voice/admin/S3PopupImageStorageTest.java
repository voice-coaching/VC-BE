package org.example.voice.admin;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.example.voice.admin.infrastructure.S3PopupImageStorage;
import org.example.voice.admin.application.PopupImageReader;
import org.example.voice.admin.domain.entity.AdminPopupImage;
import org.example.voice.admin.domain.port.*;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.example.voice.common.exception.BaseException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.core.sync.RequestBody;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class S3PopupImageStorageTest {
    @Test void privateUploadAndDeleteAreRestrictedToPopupPrefixAndFailuresAreNotIgnored() {
        var s3=mock(S3Client.class);var signer=mock(S3Presigner.class);var properties=new ObjectStorageProperties();properties.setBucket("test-bucket");
        var storage=new S3PopupImageStorage(s3,signer,properties);
        var key="popup-images/00000000-0000-4000-8000-000000000001.png";
        storage.upload(key,new byte[]{1});
        verify(s3).putObject(argThat((PutObjectRequest r)->r.bucket().equals("test-bucket")&&r.key().equals(key)&&r.contentType().equals("image/png")&&r.cacheControl().equals("private, no-store")&&r.acl()==null),any(RequestBody.class));
        assertThatThrownBy(()->storage.delete("recordings/other.wav")).isInstanceOf(BaseException.class);
        verify(s3,never()).deleteObject(any(DeleteObjectRequest.class));
        doThrow(S3Exception.builder().statusCode(503).build()).when(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThatThrownBy(()->storage.delete(key)).isInstanceOf(BaseException.class);
    }
    @Test void signedReadLifetimeNeverExceedsPopupEndAndExpiredImageIsNotSigned() {
        var now=OffsetDateTime.parse("2026-11-10T00:00:00Z");var store=mock(PopupImageStore.class);var storage=mock(PopupImageStorage.class);
        var image=AdminPopupImage.pending(1,now);image.attach();when(store.attached(1)).thenReturn(Optional.of(image));
        when(storage.url(anyString(),any())).thenReturn("https://images.example.invalid/p.png");
        var reader=new PopupImageReader(store,storage,Clock.fixed(now.toInstant(),ZoneOffset.UTC));
        assertThat(reader.image(1,now.plusSeconds(20)).expiresAt()).isEqualTo(now.plusSeconds(20));
        verify(storage).url(image.getObjectKey(),Duration.ofSeconds(20));
        assertThat(reader.image(1,now)).isNull();verifyNoMoreInteractions(storage);
    }
}
