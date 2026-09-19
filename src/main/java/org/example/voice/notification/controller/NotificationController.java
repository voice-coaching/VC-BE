package org.example.voice.notification.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.notification.application.NotificationService;
import org.example.voice.notification.domain.model.NotificationData.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api")
public class NotificationController {
    private final NotificationService service;
    public record Keys(@NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 100) String auth) {
        @Override public String toString() { return "Keys[redacted]"; }
    }
    public record RegisterPush(@NotBlank @Size(max = 2048) String endpoint, @NotNull @Valid Keys keys,
                               @NotBlank @Size(max = 512) String userAgent, @Size(max = 100) String deviceName) {
        PushDraft draft() { return new PushDraft(endpoint, keys.p256dh(), keys.auth(), userAgent, deviceName); }
        @Override public String toString() { return "RegisterPush[redacted]"; }
    }
    @GetMapping("/users/me/notification-preferences")
    public ApiResponse<Preferences> preferences(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("OK", service.preferences(user.id()));
    }
    @PatchMapping("/users/me/notification-preferences")
    public ApiResponse<Preferences> patch(@AuthenticationPrincipal LoginUser user, @RequestBody Patch patch) {
        return ApiResponse.success("OK", service.patch(user.id(), patch));
    }
    @PostMapping("/users/me/push-subscriptions") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Subscription> register(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody RegisterPush draft,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.success("OK", service.register(user.id(), draft.draft(), key));
    }
    @DeleteMapping("/users/me/push-subscriptions/{subscriptionId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long subscriptionId) {
        service.delete(user.id(), subscriptionId); return ApiResponse.success("OK", null);
    }
    @GetMapping("/notifications")
    public ApiResponse<Page> list(@AuthenticationPrincipal LoginUser user, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.success("OK", service.list(user.id(), unreadOnly, page, size));
    }
    @PatchMapping("/notifications/{notificationId}/read")
    public ApiResponse<Item> read(@AuthenticationPrincipal LoginUser user, @PathVariable Long notificationId) {
        return ApiResponse.success("OK", service.read(user.id(), notificationId));
    }
    @PostMapping("/notifications/read-all")
    public ApiResponse<ReadAll> readAll(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("OK", service.readAll(user.id()));
    }
}
