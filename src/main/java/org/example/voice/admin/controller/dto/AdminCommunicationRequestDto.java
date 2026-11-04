package org.example.voice.admin.controller.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.admin.domain.model.AdminCommunicationData.*;
import org.example.voice.admin.domain.type.*;
public final class AdminCommunicationRequestDto {
    private AdminCommunicationRequestDto() {}
    public record CampaignCreateDto(@NotNull AdminAudience audience,@NotNull AdminNotificationKind kind,
            @NotBlank @Size(max=200) String title,@NotBlank @Size(max=1000) String body,@NotBlank String deepLink,
            @Size(max=500) List<@NotNull @Positive Long> userIds,@NotBlank @Size(max=500) String reason) {
        public CampaignDraft command() { return new CampaignDraft(audience,kind,title,body,deepLink,userIds); }
    }
    public record CampaignDispatchDto(@Min(0) long version,@NotBlank @Size(max=500) String reason) {}
    public record PopupFieldsDto(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=1000) String body,
            String deepLink,@Min(0) @Max(100) int priority,@NotNull OffsetDateTime startsAt,
            @Min(1) @Max(365) int durationDays,boolean enabled) {
        public PopupDraft command() { return new PopupDraft(title,body,deepLink,priority,startsAt,durationDays,enabled); }
    }
    public record PopupCreateDto(@NotNull @Valid PopupFieldsDto popup,@NotBlank @Size(max=500) String reason) {}
    public record PopupEditDto(@Min(0) long version,@NotNull @Valid PopupFieldsDto popup,@NotBlank @Size(max=500) String reason) {}
}
