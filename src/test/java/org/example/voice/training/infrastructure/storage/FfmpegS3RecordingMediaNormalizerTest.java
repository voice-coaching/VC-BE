package org.example.voice.training.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.VisualProcessingAuthorizationData;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FfmpegS3RecordingMediaNormalizerTest {
    @TempDir Path temporaryDirectory;
    @Mock private S3Client s3Client;

    @Test
    void sandboxLauncherDeniesNetworkSocketCreation() throws Exception {
        Process process = new ProcessBuilder(
                mediaProperties().getSandboxPythonBinary(),
                Path.of("scripts/media_sandbox.py").toAbsolutePath().normalize().toString(),
                "--self-test"
        ).start();

        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8
        ).trim()).isEqualTo("{\"networkIsolation\": \"seccomp_socket_denied\"}");
    }

    @Test
    void parsesSupportedMp4AndStoresCanonicalAudioWithDigest() throws Exception {
        assumeFfmpeg();
        Path source = createVideo("libx264");
        stubStorageDownload(source, "video/mp4");
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
        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(get.capture(), any(Path.class));
        assertThat(get.getValue().ifMatch()).isEqualTo("source-etag");
        assertThat(get.getValue().versionId()).isEqualTo("source-version");
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(delete.capture());
        assertThat(delete.getValue().ifMatch()).isEqualTo("source-etag");
        assertThat(delete.getValue().versionId()).isEqualTo("source-version");
        try (var workspaces = Files.list(workspaceRoot())) {
            assertThat(workspaces).isEmpty();
        }
    }

    @Test
    void normalizesHevcQuickTimeForTheCanonicalAiVideoContract() throws Exception {
        assertSupportedVideo(
                createVideo("hevc-mov", ".mov", "libx265", "aac", "mov"),
                "video/quicktime"
        );
    }

    @Test
    void normalizesVp8VorbisWebmForTheCanonicalAiVideoContract() throws Exception {
        assertSupportedVideo(
                createVideo("vp8-vorbis", ".webm", "libvpx", "libvorbis", "webm"),
                "video/webm"
        );
    }

    @Test
    void normalizesVp9OpusWebmForTheCanonicalAiVideoContract() throws Exception {
        assertSupportedVideo(
                createVideo("vp9-opus", ".webm", "libvpx-vp9", "libopus", "webm"),
                "video/webm"
        );
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "VC_BE_PRIVATE_MEDIA_SAMPLE", matches = ".+")
    void normalizesAnApprovedPrivateDeviceCaptureWithoutCommittingIt() throws Exception {
        Path source = Path.of(System.getenv("VC_BE_PRIVATE_MEDIA_SAMPLE"))
                .toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
        assertThat(Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)).isTrue();

        assertSupportedVideo(source, "video/mp4");
    }

    @Test
    void rejectsMp4WithUnapprovedVideoCodecBeforeUpload() throws Exception {
        assumeFfmpeg();
        Path source = createVideo("mpeg4");
        stubStorageDownload(source, "video/mp4");

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
    void rejectsObjectOutsideAuthenticatedOwnerPrefixBeforeStorageAccess() throws Exception {
        assertThatThrownBy(() -> normalizer(ownerPrefixGuardMediaProperties()).normalize(
                9L,
                7L,
                "recordings/users/10/sessions/7/source.mp4",
                "video/mp4",
                1_000L,
                visualAuthorization()
        )).isInstanceOfSatisfying(BaseException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECORDING_ACCESS_DENIED));

        verifyNoInteractions(s3Client);
    }

    private FfmpegS3RecordingMediaNormalizer normalizer() {
        return normalizer(mediaProperties());
    }

    private FfmpegS3RecordingMediaNormalizer normalizer(MediaNormalizationProperties mediaProperties) {
        return new FfmpegS3RecordingMediaNormalizer(
                storageProperties(),
                mediaProperties,
                s3Client,
                new ObjectMapper()
        );
    }

    private void assertSupportedVideo(Path source, String declaredMimeType) throws Exception {
        assumeFfmpeg();
        stubStorageDownload(source, declaredMimeType);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        NormalizedRecordingData normalized = normalizer().normalize(
                9L,
                7L,
                "recordings/users/9/sessions/7/source" + extension(source),
                declaredMimeType,
                Files.size(source),
                visualAuthorization()
        );

        assertThat(normalized.mimeType()).isEqualTo("audio/wav");
        assertThat(normalized.durationMs()).isBetween(
                mediaProperties().getMinimumDurationMs(),
                mediaProperties().getMaximumDurationMs()
        );
        assertThat(normalized.visual()).isNotNull();
        assertThat(normalized.visual().mimeType()).isEqualTo("video/mp4");
        assertThat(normalized.visual().visualSha256()).matches("[0-9a-f]{64}");
        assertThat(normalized.visual().consentReceiptSha256()).isEqualTo("f".repeat(64));
        verify(s3Client, org.mockito.Mockito.times(2))
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private void stubStorageDownload(Path source, String mimeType) {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentLength(source.toFile().length())
                        .contentType(mimeType)
                        .eTag("source-etag")
                        .versionId("source-version")
                        .build()
        );
        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class))).thenAnswer(invocation -> {
            Path destination = invocation.getArgument(1);
            Files.copy(source, destination);
            return GetObjectResponse.builder()
                    .contentLength(Files.size(source))
                    .contentType(mimeType)
                    .eTag("source-etag")
                    .versionId("source-version")
                    .build();
        });
    }

    private Path createVideo(String codec) throws Exception {
        return createVideo(codec, ".mp4", codec, "aac", "mp4");
    }

    private Path createVideo(
            String name,
            String extension,
            String videoCodec,
            String audioCodec,
            String format
    ) throws Exception {
        assumeFfmpeg();
        Path output = temporaryDirectory.resolve(name + extension);
        java.util.ArrayList<String> command = new java.util.ArrayList<>(java.util.List.of(
                "/usr/bin/ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "testsrc2=size=320x240:rate=25:duration=1",
                "-f", "lavfi",
                "-i", "sine=frequency=1000:sample_rate=48000:duration=1",
                "-c:v", videoCodec,
                "-pix_fmt", "yuv420p",
                "-threads", "1"
        ));
        if ("libx265".equals(videoCodec)) {
            command.addAll(java.util.List.of(
                    "-tag:v", "hvc1",
                    "-x265-params", "pools=1:frame-threads=1:log-level=error"
            ));
        }
        command.addAll(java.util.List.of(
                "-c:a", audioCodec,
                "-shortest",
                "-f", format,
                output.toString()
        ));
        Process process = new ProcessBuilder(command).start();
        assertThat(process.waitFor()).isZero();
        return output;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.'));
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

    private MediaNormalizationProperties ownerPrefixGuardMediaProperties() throws Exception {
        Path unusedExecutable = temporaryDirectory.resolve("unused-media-executable");
        Files.createFile(unusedExecutable);
        Files.setPosixFilePermissions(unusedExecutable, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE
        ));
        MediaNormalizationProperties properties = mediaProperties();
        properties.setSandboxPythonBinary(unusedExecutable.toString());
        properties.setFfmpegBinary(unusedExecutable.toString());
        properties.setFfprobeBinary(unusedExecutable.toString());
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
