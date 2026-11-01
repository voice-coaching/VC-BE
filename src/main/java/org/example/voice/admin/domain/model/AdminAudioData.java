package org.example.voice.admin.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminAudioData {
    private AdminAudioData() {}
    public static final long MAX_BYTES=20L*1024*1024;
    public static final int MAX_DURATION_MS=180_000;
    public record UploadCommand(long version,String fileName,String contentType,long size,String sha256,String reason) {}
    public record Upload(UUID id,long actorId,long contentId,String extension,String contentType,long size,
                         String sha256,String scriptSha256,OffsetDateTime expiresAt,Long audioId,String completionDigest) {}
    public record SignedUpload(String url,Map<String,String> headers,OffsetDateTime expiresAt) {}
    public record UploadReceipt(UUID uploadId,String uploadUrl,Map<String,String> requiredHeaders,OffsetDateTime expiresAt) {}
    public record VerifiedAudio(String objectKey,int durationMs) {}
    public record Audio(long id,long contentId,String speakerName,String speakerType,Integer durationMs,boolean primary,
                        String objectKey,OffsetDateTime createdAt) {}
    public record AudioResult(Audio audio,long contentVersion,boolean created) {}
    public record AudioList(List<Audio> items,long contentVersion) {}
    public record Playback(long audioId,String playbackUrl,OffsetDateTime expiresAt) {}
}
