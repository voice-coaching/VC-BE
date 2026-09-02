package org.example.voice.training.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.VisualProcessingAuthorizationData;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FfmpegS3RecordingMediaNormalizerTest {
    @TempDir Path temporaryDirectory;
    @Mock private S3Client s3Client;

    @Test
    void parsesSupportedMp4AndStoresCanonicalAudioWithDigest() throws Exception {
        assumeFfmpeg();
        Path source = createVideo("libx264");
        stubStorageDownload(source);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        NormalizedRecordingData normalized = normalizer().normalize(
                9L,
                7L,
                "recordings/users/9/sessions/7/source.mp4",
                "video/mp4",
                Files.size(source),
                visualAuthorization()
        );

        assertThat(normalized.objectKey())
                .startsWith("recordings/analysis-audio/")
                .endsWith(".wav");
        assertThat(normalized.mimeType()).isEqualTo("audio/wav");
        assertThat(normalized.audioSha256()).matches("[0-9a-f]{64}");
        assertThat(normalized.durationMs()).isBetween(900, 1_100);
        assertThat(normalized.qualityStatus()).isEqualTo(RecordingQualityStatus.PASS);

        assertThat(normalized.visual()).isNotNull();
        assertThat(normalized.visual().objectKey()).startsWith("recordings/analysis-video/");
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.times(2))
                .putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getAllValues()).extracting(PutObjectRequest::contentType)
                .containsExactlyInAnyOrder("audio/wav", "video/mp4");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        try (var workspaces = Files.list(workspaceRoot())) {
            assertThat(workspaces).isEmpty();
        }
    }

    @Test
    void rejectsMp4WithUnapprovedVideoCodecBeforeUpload() throws Exception {
        assumeFfmpeg();
        Path source = createVideo("mpeg4");
        stubStorageDownload(source);

        assertThatThrownBy(() -> normalizer().normalize(
                9L,
                7L,
                "recordings/users/9/sessions/7/source.mp4",
                "video/mp4",
                Files.size(source),
                visualAuthorization()
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NORMALIZATION_FAILED));

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void rejectsObjectOutsideAuthenticatedOwnerPrefixBeforeStorageAccess() {
        assertThatThrownBy(() -> normalizer().normalize(
                9L,
                7L,
                "recordings/users/10/sessions/7/source.mp4",
                "video/mp4",
                1_000L,
                visualAuthorization()
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));

        verify(s3Client, never()).getObject(any(GetObjectRequest.class), any(Path.class));
    }

    private FfmpegS3RecordingMediaNormalizer normalizer() {
        return new FfmpegS3RecordingMediaNormalizer(
                storageProperties(),
                mediaProperties(),
                s3Client,
                new ObjectMapper()
        );
    }

    private void stubStorageDownload(Path source) {
        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class))).thenAnswer(invocation -> {
            Path destination = invocation.getArgument(1);
            Files.copy(source, destination);
            return GetObjectResponse.builder().contentLength(Files.size(source)).build();
        });
    }

    private Path createVideo(String codec) throws Exception {
        Path output = temporaryDirectory.resolve(codec + ".mp4");
        Process process = new ProcessBuilder(
                "/usr/bin/ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "color=c=black:s=64x64:d=1",
                "-f", "lavfi",
                "-i", "sine=frequency=1000:duration=1",
                "-c:v", codec,
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-shortest",
                output.toString()
        ).start();
        assertThat(process.waitFor()).isZero();
        return output;
    }

    private ObjectStorageProperties storageProperties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEnabled(true);
        properties.setBucket("private-recordings");
        properties.setRegion("ap-northeast-2");
        properties.setRecordingsPrefix("recordings/");
        return properties;
    }

    private MediaNormalizationProperties mediaProperties() {
        MediaNormalizationProperties properties = new MediaNormalizationProperties();
        properties.setEnabled(true);
        properties.setWorkspaceRoot(workspaceRoot());
        properties.setFfmpegBinary("/usr/bin/ffmpeg");
        properties.setFfprobeBinary("/usr/bin/ffprobe");
        return properties;
    }

    private Path workspaceRoot() {
        return temporaryDirectory.resolve("workspaces");
    }

    private static void assumeFfmpeg() {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffmpeg")));
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffprobe")));
    }

    private static VisualProcessingAuthorizationData visualAuthorization() {
        return new VisualProcessingAuthorizationData(
                "f".repeat(64), "voice-video-processing-consent-v1"
        );
    }
}
