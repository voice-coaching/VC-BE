package org.example.voice.admin.controller.dto;

import org.example.voice.admin.domain.model.AdminAudioData.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class AdminAudioResponseDto {
    private AdminAudioResponseDto() {}
    public record AdminAudioUploadResponseDto(UUID uploadId,String uploadUrl,Map<String,String> requiredHeaders,OffsetDateTime expiresAt) {
        public static AdminAudioUploadResponseDto from(UploadReceipt r) { return new AdminAudioUploadResponseDto(r.uploadId(),r.uploadUrl(),r.requiredHeaders(),r.expiresAt()); }
    }
    public record AudioDto(long audioId,long contentId,String speakerName,String speakerType,Integer durationMs,
                            boolean primary,long contentVersion,OffsetDateTime createdAt) {
        public static AudioDto from(AudioResult r) { return from(r.audio(),r.contentVersion()); }
        public static AudioDto from(Audio a,long version) { return new AudioDto(a.id(),a.contentId(),a.speakerName(),a.speakerType(),a.durationMs(),a.primary(),version,a.createdAt()); }
    }
    public record ListDto(List<AudioDto> items,long contentVersion) {
        public static ListDto from(AudioList list) { return new ListDto(list.items().stream().map(a -> AudioDto.from(a,list.contentVersion())).toList(),list.contentVersion()); }
    }
    public record PlaybackDto(long audioId,String playbackUrl,OffsetDateTime expiresAt) {
        public static PlaybackDto from(Playback p) { return new PlaybackDto(p.audioId(),p.playbackUrl(),p.expiresAt()); }
    }
}
