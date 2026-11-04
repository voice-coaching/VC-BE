package org.example.voice.admin.controller.dto;

import jakarta.validation.constraints.*;
import org.example.voice.admin.domain.model.AdminAudioData.UploadCommand;
import java.util.UUID;

public final class AdminAudioRequestDto {
    private AdminAudioRequestDto() {}
    public record AdminAudioUploadRequestDto(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=255) String fileName,
            @NotBlank String contentType, @Min(1) @Max(20971520) long fileSizeBytes,
            @NotNull @Pattern(regexp="[0-9a-f]{64}") String sha256, @NotBlank @Size(max=500) String reason) {
        public UploadCommand command() { return new UploadCommand(version,fileName,contentType,fileSizeBytes,sha256,reason); }
    }
    public record RegisterDto(@NotNull UUID uploadId,@NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max=255) String speakerName,@NotNull Boolean primary,@NotBlank @Size(max=500) String reason) {}
    public record PrimaryDto(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=500) String reason) {}
    public record AudioDeleteDto(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=500) String reason) {}
}
