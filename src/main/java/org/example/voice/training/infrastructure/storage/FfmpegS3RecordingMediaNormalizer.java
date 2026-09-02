package org.example.voice.training.infrastructure.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.NormalizedVisualData;
import org.example.voice.training.domain.model.VisualProcessingAuthorizationData;
import org.example.voice.training.domain.port.RecordingMediaNormalizationPort;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Downloads an owner-bound upload, validates its real container/codecs, and
 * stores a backend-only 16 kHz mono PCM WAV for the AI worker.
 */
@Component
@ConditionalOnProperty(prefix = "storage.media-normalization", name = "enabled", havingValue = "true")
public class FfmpegS3RecordingMediaNormalizer implements RecordingMediaNormalizationPort {
    private static final int MAXIMUM_PROBE_OUTPUT_BYTES = 64 * 1024;
    private static final String CANONICAL_CODEC = "pcm_s16le";
    private static final Set<String> MP4_VIDEO_CODECS = Set.of("h264", "hevc");
    private static final Set<String> WEBM_VIDEO_CODECS = Set.of("vp8", "vp9");
    private static final Set<String> WEBM_AUDIO_CODECS = Set.of("opus", "vorbis");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> READ_ONLY_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ
    );

    private final ObjectStorageProperties storage;
    private final MediaNormalizationProperties media;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public FfmpegS3RecordingMediaNormalizer(
            ObjectStorageProperties storage,
            MediaNormalizationProperties media,
            S3Client recordingS3Client,
            ObjectMapper objectMapper
    ) {
        this.storage = storage;
        this.media = media;
        this.s3Client = recordingS3Client;
        this.objectMapper = objectMapper;
        validateConfiguration(media);
    }

    @Override
    public NormalizedRecordingData normalize(
            Long userId,
            Long sessionId,
            String sourceObjectKey,
            String declaredMimeType,
            long declaredFileSizeBytes,
            VisualProcessingAuthorizationData visualAuthorization
    ) {
        requireOwnerPrefix(userId, sessionId, sourceObjectKey);
        boolean video = declaredMimeType != null && declaredMimeType.startsWith("video/");
        if (video != (visualAuthorization != null)) {
            throw new BaseException(ErrorCode.VIDEO_PROCESSING_CONSENT_REQUIRED);
        }
        if (declaredFileSizeBytes <= 0 || declaredFileSizeBytes > media.getMaximumInputBytes()) {
            throw new BaseException(ErrorCode.AUDIO_FILE_TOO_LARGE);
        }
        Path workspace = createWorkspace();
        Path source = workspace.resolve("source.media");
        Path canonical = workspace.resolve("canonical.wav");
        Path canonicalVisual = workspace.resolve("canonical.mp4");
        String normalizedObjectKey = storage.getRecordingsPrefix()
                + "analysis-audio/" + UUID.randomUUID() + ".wav";
        String visualObjectKey = video
                ? storage.getRecordingsPrefix() + "analysis-video/" + UUID.randomUUID() + ".mp4"
                : null;
        boolean sourceDownloaded = false;
        boolean normalizedUploaded = false;
        boolean visualUploaded = false;
        SourceObjectInspection sourceInspection = null;
        RuntimeException failure = null;
        try {
            sourceInspection = inspectSourceObject(
                    sourceObjectKey, declaredMimeType, declaredFileSizeBytes
            );
            GetObjectRequest.Builder getRequest = GetObjectRequest.builder()
                    .bucket(storage.getBucket())
                    .key(sourceObjectKey)
                    .ifMatch(sourceInspection.eTag());
            if (sourceInspection.versionId() != null) {
                getRequest.versionId(sourceInspection.versionId());
            }
            GetObjectResponse download = s3Client.getObject(
                    getRequest.build(),
                    source
            );
            sourceDownloaded = true;
            requireSameSourceObject(download, sourceInspection);
            Files.setPosixFilePermissions(source, READ_ONLY_FILE_PERMISSIONS);
            requireRestrictedRegularFile(source, declaredFileSizeBytes, media.getMaximumInputBytes());
            ProbeDocument probe = probe(source);
            long probedDurationMs = validateProbe(probe, declaredMimeType);
            Path audioSource = source;
            NormalizedVisualData visual = null;
            if (video) {
                canonicalizeVideo(source, canonicalVisual, declaredMimeType);
                Files.setPosixFilePermissions(canonicalVisual, READ_ONLY_FILE_PERMISSIONS);
                requireRestrictedRegularFile(canonicalVisual, null, media.getMaximumInputBytes());
                ProbeDocument canonicalProbe = probe(canonicalVisual);
                long canonicalDurationMs = validateProbe(canonicalProbe, NormalizedVisualData.CANONICAL_MIME_TYPE);
                if (Math.abs(canonicalDurationMs - probedDurationMs) > 1_500) {
                    throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
                }
                String visualDigest = sha256(canonicalVisual);
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(storage.getBucket())
                                .key(visualObjectKey)
                                .contentType(NormalizedVisualData.CANONICAL_MIME_TYPE)
                                .contentLength(Files.size(canonicalVisual))
                                .metadata(java.util.Map.of("sha256", visualDigest))
                                .build(),
                        RequestBody.fromFile(canonicalVisual)
                );
                visualUploaded = true;
                visual = new NormalizedVisualData(
                        visualObjectKey,
                        NormalizedVisualData.CANONICAL_MIME_TYPE,
                        Files.size(canonicalVisual),
                        visualDigest,
                        visualAuthorization.receiptSha256(),
                        visualAuthorization.policyRevision()
                );
                audioSource = canonicalVisual;
            }
            transcode(audioSource, canonical);
            Files.setPosixFilePermissions(canonical, READ_ONLY_FILE_PERMISSIONS);
            requireRestrictedRegularFile(canonical, null, media.getMaximumNormalizedBytes());
            PcmQuality quality = inspectCanonicalWav(canonical);
            if (Math.abs(quality.durationMs() - probedDurationMs) > 1_500) {
                throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            }
            String digest = sha256(canonical);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storage.getBucket())
                            .key(normalizedObjectKey)
                            .contentType(NormalizedRecordingData.CANONICAL_MIME_TYPE)
                            .contentLength(Files.size(canonical))
                            .metadata(java.util.Map.of("sha256", digest))
                            .build(),
                    RequestBody.fromFile(canonical)
            );
            normalizedUploaded = true;
            return new NormalizedRecordingData(
                    normalizedObjectKey,
                    NormalizedRecordingData.CANONICAL_MIME_TYPE,
                    Files.size(canonical),
                    quality.durationMs(),
                    digest,
                    quality.status(),
                    quality.volumeScore(),
                    null,
                    visual
            );
        } catch (BaseException error) {
            failure = error;
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failure = new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            throw failure;
        } catch (Exception error) {
            failure = new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            throw failure;
        } finally {
            RuntimeException mandatoryCleanupFailure = null;
            if (sourceDownloaded) {
                try {
                    DeleteObjectRequest.Builder deleteRequest = DeleteObjectRequest.builder()
                            .bucket(storage.getBucket())
                            .key(sourceObjectKey)
                            .ifMatch(sourceInspection.eTag());
                    if (sourceInspection.versionId() != null) {
                        deleteRequest.versionId(sourceInspection.versionId());
                    }
                    s3Client.deleteObject(deleteRequest.build());
                } catch (RuntimeException cleanupFailure) {
                    mandatoryCleanupFailure = new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
                }
            }
            if ((failure != null || mandatoryCleanupFailure != null) && normalizedUploaded) {
                try {
                    deleteNormalizedObject(userId, sessionId, normalizedObjectKey);
                } catch (RuntimeException cleanupFailure) {
                    if (mandatoryCleanupFailure == null) {
                        mandatoryCleanupFailure = cleanupFailure;
                    } else {
                        mandatoryCleanupFailure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if ((failure != null || mandatoryCleanupFailure != null) && visualUploaded) {
                try {
                    deleteNormalizedObject(userId, sessionId, visualObjectKey);
                } catch (RuntimeException cleanupFailure) {
                    if (mandatoryCleanupFailure == null) {
                        mandatoryCleanupFailure = cleanupFailure;
                    } else {
                        mandatoryCleanupFailure.addSuppressed(cleanupFailure);
                    }
                }
            }
            RuntimeException workspaceCleanupFailure = deleteWorkspace(workspace);
            if (workspaceCleanupFailure != null) {
                if (mandatoryCleanupFailure == null) {
                    mandatoryCleanupFailure = workspaceCleanupFailure;
                } else {
                    mandatoryCleanupFailure.addSuppressed(workspaceCleanupFailure);
                }
            }
            if (mandatoryCleanupFailure != null) {
                if (failure != null) {
                    mandatoryCleanupFailure.addSuppressed(failure);
                }
                throw mandatoryCleanupFailure;
            }
        }
    }

    private SourceObjectInspection inspectSourceObject(
            String objectKey,
            String declaredMimeType,
            long declaredFileSizeBytes
    ) {
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(storage.getBucket())
                .key(objectKey)
                .build());
        String eTag = response.eTag();
        String versionId = normalizeVersionId(response.versionId());
        if (response.contentLength() == null
                || response.contentLength() != declaredFileSizeBytes
                || response.contentType() == null
                || !response.contentType().equalsIgnoreCase(declaredMimeType)
                || !isSafeObjectIdentity(eTag)
                || (versionId != null && !isSafeObjectIdentity(versionId))) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
        return new SourceObjectInspection(
                eTag,
                versionId,
                response.contentLength(),
                response.contentType()
        );
    }

    private static void requireSameSourceObject(
            GetObjectResponse response,
            SourceObjectInspection inspection
    ) {
        if (response.contentLength() == null
                || response.contentLength() != inspection.contentLength()
                || response.contentType() == null
                || !response.contentType().equalsIgnoreCase(inspection.contentType())
                || !inspection.eTag().equals(response.eTag())
                || (inspection.versionId() != null
                    && !inspection.versionId().equals(response.versionId()))) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
    }

    private static String normalizeVersionId(String versionId) {
        return versionId == null || versionId.isBlank() || versionId.equals("null")
                ? null
                : versionId;
    }

    private static boolean isSafeObjectIdentity(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 1_024
                && value.indexOf('\r') < 0
                && value.indexOf('\n') < 0;
    }

    @Override
    public void deleteNormalizedObject(Long userId, Long sessionId, String objectKey) {
        if (userId == null || userId <= 0 || sessionId == null || sessionId <= 0
                || !isOpaqueAnalysisObjectKey(objectKey)) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storage.getBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException error) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }

    private ProbeDocument probe(Path source) throws IOException, InterruptedException {
        List<String> command = List.of(
                media.getFfprobeBinary(),
                "-v", "error",
                "-protocol_whitelist", "file,pipe",
                "-show_entries",
                "format=format_name,duration:stream=codec_type,codec_name,sample_rate,channels",
                "-of", "json",
                source.toString()
        );
        byte[] output = run(command, true);
        try {
            return objectMapper.readValue(output, ProbeDocument.class);
        } catch (IOException error) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
    }

    private void transcode(Path source, Path canonical) throws IOException, InterruptedException {
        List<String> command = List.of(
                media.getFfmpegBinary(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-protocol_whitelist", "file,pipe",
                "-i", source.toString(),
                "-map", "0:a:0",
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-c:a", CANONICAL_CODEC,
                "-f", "wav",
                canonical.toString()
        );
        run(command, false);
    }

    private void canonicalizeVideo(Path source, Path canonical, String declaredMimeType)
            throws IOException, InterruptedException {
        List<String> codecs = "video/webm".equals(declaredMimeType)
                ? List.of(
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-pix_fmt", "yuv420p",
                        "-c:a", "aac",
                        "-ac", "1",
                        "-ar", "16000",
                        "-b:a", "64k"
                )
                : List.of("-c", "copy");
        java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of(
                media.getFfmpegBinary(),
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-protocol_whitelist", "file,pipe",
                "-i", source.toString(),
                "-map", "0:v:0",
                "-map", "0:a:0",
                "-map_metadata", "-1",
                "-map_chapters", "-1"
        ));
        command.addAll(codecs);
        command.addAll(List.of("-movflags", "+faststart", "-f", "mp4", canonical.toString()));
        run(command, false);
    }

    private byte[] run(List<String> command, boolean captureOutput) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        if (!captureOutput) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        builder.environment().clear();
        builder.environment().put("LANG", "C");
        builder.environment().put("LC_ALL", "C");
        builder.environment().put("PATH", "/usr/bin:/bin");
        Process process = builder.start();
        byte[][] captured = new byte[1][];
        IOException[] readFailure = new IOException[1];
        Thread outputReader = null;
        if (captureOutput) {
            outputReader = Thread.startVirtualThread(() -> {
                try (InputStream stream = process.getInputStream()) {
                    captured[0] = stream.readNBytes(MAXIMUM_PROBE_OUTPUT_BYTES + 1);
                } catch (IOException error) {
                    readFailure[0] = error;
                }
            });
        }
        try {
            boolean exited = process.waitFor(media.getProcessTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            }
            if (outputReader != null) {
                outputReader.join(5_000);
                if (outputReader.isAlive() || readFailure[0] != null || captured[0] == null) {
                    outputReader.interrupt();
                    throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
                }
                if (captured[0].length > MAXIMUM_PROBE_OUTPUT_BYTES) {
                    throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
                }
            }
            if (process.exitValue() != 0) {
                throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            }
            return captured[0] == null ? new byte[0] : captured[0];
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private long validateProbe(ProbeDocument probe, String declaredMimeType) {
        if (probe == null || probe.format() == null || probe.streams() == null) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
        long durationMs = parseDurationMs(probe.format().duration());
        if (durationMs < media.getMinimumDurationMs() || durationMs > media.getMaximumDurationMs()) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
        List<ProbeStream> audioStreams = probe.streams().stream()
                .filter(stream -> "audio".equals(stream.codecType()))
                .toList();
        List<ProbeStream> videoStreams = probe.streams().stream()
                .filter(stream -> "video".equals(stream.codecType()))
                .toList();
        if (audioStreams.size() != 1) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
        String format = lower(probe.format().formatName());
        String audioCodec = lower(audioStreams.getFirst().codecName());
        switch (declaredMimeType) {
            case "audio/wav" -> requireFormat(
                    format.contains("wav") && audioCodec.startsWith("pcm_") && videoStreams.isEmpty()
            );
            case "audio/mpeg" -> requireFormat(
                    format.contains("mp3") && audioCodec.equals("mp3") && videoStreams.isEmpty()
            );
            case "audio/webm" -> requireFormat(
                    format.contains("webm") && WEBM_AUDIO_CODECS.contains(audioCodec) && videoStreams.isEmpty()
            );
            case "video/mp4", "video/quicktime" -> requireFormat(
                    format.contains("mov")
                            && videoStreams.size() == 1
                            && MP4_VIDEO_CODECS.contains(lower(videoStreams.getFirst().codecName()))
                            && audioCodec.equals("aac")
            );
            case "video/webm" -> requireFormat(
                    format.contains("webm")
                            && videoStreams.size() == 1
                            && WEBM_VIDEO_CODECS.contains(lower(videoStreams.getFirst().codecName()))
                            && WEBM_AUDIO_CODECS.contains(audioCodec)
            );
            default -> throw new BaseException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT);
        }
        return durationMs;
    }

    private PcmQuality inspectCanonicalWav(Path path) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, header);
            if (!ascii(header.array(), 0, 4).equals("RIFF") || !ascii(header.array(), 8, 4).equals("WAVE")) {
                throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            }
            Integer sampleRate = null;
            Integer channels = null;
            Integer bitsPerSample = null;
            Long dataOffset = null;
            Integer dataSize = null;
            while (channel.position() + 8 <= channel.size()) {
                ByteBuffer chunk = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                readFully(channel, chunk);
                String chunkName = ascii(chunk.array(), 0, 4);
                int chunkSize = chunk.getInt(4);
                if (chunkSize < 0 || channel.position() + chunkSize > channel.size()) {
                    throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
                }
                if (chunkName.equals("fmt ")) {
                    if (chunkSize < 16) {
                        throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
                    }
                    ByteBuffer format = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                    readFully(channel, format);
                    if (Short.toUnsignedInt(format.getShort(0)) != 1) {
                        throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
                    }
                    channels = Short.toUnsignedInt(format.getShort(2));
                    sampleRate = format.getInt(4);
                    bitsPerSample = Short.toUnsignedInt(format.getShort(14));
                    channel.position(channel.position() + chunkSize - 16L);
                } else if (chunkName.equals("data")) {
                    dataOffset = channel.position();
                    dataSize = chunkSize;
                    channel.position(channel.position() + chunkSize);
                } else {
                    channel.position(channel.position() + chunkSize);
                }
                if ((chunkSize & 1) == 1 && channel.position() < channel.size()) {
                    channel.position(channel.position() + 1);
                }
            }
            if (!Integer.valueOf(16_000).equals(sampleRate)
                    || !Integer.valueOf(1).equals(channels)
                    || !Integer.valueOf(16).equals(bitsPerSample)
                    || dataOffset == null
                    || dataSize == null
                    || dataSize <= 0
                    || (dataSize & 1) == 1) {
                throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            }
            channel.position(dataOffset);
            long sampleCount = dataSize / 2L;
            long activeSamples = 0;
            long clippedSamples = 0;
            double squaredSum = 0.0;
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
            long remaining = dataSize;
            while (remaining > 0) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                readFully(channel, buffer);
                buffer.flip();
                while (buffer.remaining() >= 2) {
                    int sample = buffer.getShort();
                    double normalized = sample / 32768.0;
                    squaredSum += normalized * normalized;
                    if (Math.abs(sample) >= 512) {
                        activeSamples++;
                    }
                    if (Math.abs(sample) >= 32_700) {
                        clippedSamples++;
                    }
                }
                remaining -= buffer.limit();
            }
            int durationMs = Math.toIntExact(Math.round(sampleCount * 1_000.0 / sampleRate));
            double rms = Math.sqrt(squaredSum / sampleCount);
            double activeRatio = activeSamples / (double) sampleCount;
            double clippedRatio = clippedSamples / (double) sampleCount;
            RecordingQualityStatus status;
            if (durationMs < media.getMinimumDurationMs()) {
                status = RecordingQualityStatus.TOO_SHORT;
            } else if (rms < 0.003 || activeRatio < 0.005) {
                status = RecordingQualityStatus.NO_SPEECH;
            } else if (rms < 0.01) {
                status = RecordingQualityStatus.LOW_VOLUME;
            } else if (clippedRatio > 0.02) {
                status = RecordingQualityStatus.FAILED;
            } else {
                status = RecordingQualityStatus.PASS;
            }
            double decibels = rms == 0.0 ? -60.0 : Math.max(-60.0, 20.0 * Math.log10(rms));
            BigDecimal volumeScore = BigDecimal.valueOf((decibels + 60.0) / 60.0 * 100.0)
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            return new PcmQuality(durationMs, status, volumeScore);
        }
    }

    private static void validateConfiguration(MediaNormalizationProperties media) {
        Duration timeout = media.getProcessTimeout();
        if (!media.isEnabled()
                || media.getWorkspaceRoot() == null
                || !media.getWorkspaceRoot().isAbsolute()
                || Path.of("/").equals(media.getWorkspaceRoot())
                || media.getFfmpegBinary() == null
                || !Path.of(media.getFfmpegBinary()).isAbsolute()
                || !isExecutableRegularFile(Path.of(media.getFfmpegBinary()))
                || media.getFfprobeBinary() == null
                || !Path.of(media.getFfprobeBinary()).isAbsolute()
                || !isExecutableRegularFile(Path.of(media.getFfprobeBinary()))
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0
                || media.getMaximumInputBytes() <= 0
                || media.getMaximumNormalizedBytes() <= 0
                || media.getMinimumDurationMs() <= 0
                || media.getMaximumDurationMs() <= media.getMinimumDurationMs()) {
            throw new IllegalStateException("media_normalization_configuration_invalid");
        }
    }

    private static boolean isExecutableRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && Files.isExecutable(path);
    }

    private Path createWorkspace() {
        try {
            Files.createDirectories(media.getWorkspaceRoot());
            if (Files.isSymbolicLink(media.getWorkspaceRoot())) {
                throw new IOException();
            }
            Files.setPosixFilePermissions(media.getWorkspaceRoot(), DIRECTORY_PERMISSIONS);
            Path workspace = Files.createTempDirectory(media.getWorkspaceRoot(), "recording-");
            Files.setPosixFilePermissions(workspace, DIRECTORY_PERMISSIONS);
            return workspace.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException error) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }

    private static RuntimeException deleteWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try (var paths = Files.walk(workspace)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
            return null;
        } catch (IOException error) {
            return new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }

    private static void requireRestrictedRegularFile(Path path, Long expectedSize, long maximumSize)
            throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
        long size = Files.size(path);
        if (size <= 0 || size > maximumSize || (expectedSize != null && size != expectedSize)) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
    }

    private String requireOwnerPrefix(Long userId, Long sessionId, String objectKey) {
        if (userId == null || userId <= 0 || sessionId == null || sessionId <= 0 || objectKey == null) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
        String ownerPrefix = "%susers/%d/sessions/%d/".formatted(
                storage.getRecordingsPrefix(), userId, sessionId
        );
        if (!objectKey.startsWith(ownerPrefix)
                || objectKey.length() <= ownerPrefix.length()
                || objectKey.contains("\\")
                || java.util.Arrays.stream(objectKey.split("/"))
                        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
        return ownerPrefix;
    }

    private boolean isOpaqueAnalysisObjectKey(String objectKey) {
        if (objectKey == null || objectKey.contains("\\")) {
            return false;
        }
        String audioPattern = java.util.regex.Pattern.quote(storage.getRecordingsPrefix())
                + "analysis-audio/[0-9a-fA-F-]{36}\\.wav";
        String videoPattern = java.util.regex.Pattern.quote(storage.getRecordingsPrefix())
                + "analysis-video/[0-9a-fA-F-]{36}\\.mp4";
        return objectKey.matches(audioPattern) || objectKey.matches(videoPattern);
    }

    private static long parseDurationMs(String value) {
        try {
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds) || seconds <= 0) {
                throw new NumberFormatException();
            }
            return Math.round(seconds * 1_000.0);
        } catch (NumberFormatException error) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
    }

    private static void requireFormat(boolean accepted) {
        if (!accepted) {
            throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String ascii(byte[] value, int offset, int length) {
        return new String(value, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void readFully(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED);
            }
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProbeDocument(ProbeFormat format, List<ProbeStream> streams) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProbeFormat(
            @JsonProperty("format_name") String formatName,
            String duration
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProbeStream(
            @JsonProperty("codec_type") String codecType,
            @JsonProperty("codec_name") String codecName,
            @JsonProperty("sample_rate") String sampleRate,
            Integer channels
    ) {
    }

    private record SourceObjectInspection(
            String eTag,
            String versionId,
            long contentLength,
            String contentType
    ) {
    }

    private record PcmQuality(
            int durationMs,
            RecordingQualityStatus status,
            BigDecimal volumeScore
    ) {
    }
}
