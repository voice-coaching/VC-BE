package org.example.voice.notification.controller;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.infrastructure.NativeFcmTokenCipher;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.type.UserStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/native-fcm-devices")
@RequiredArgsConstructor
public class NativeFcmController {
    private final EntityManager em;
    private final UserReader users;
    private final NativeFcmTokenCipher cipher;

    public record Registration(@NotNull UUID installationId, @NotBlank @Size(max = 4096) String token) {
        @Override public String toString() { return "Registration[redacted]"; }
    }

    @PutMapping
    @Transactional
    public ApiResponse<Void> register(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody Registration input) {
        var account = users.findByIdForUpdate(user.id()).orElseThrow(NotificationException::missing);
        if (account.getStatus() != UserStatus.ACTIVE) throw new NotificationException(403, "FORBIDDEN");
        if (input.token().codePoints().anyMatch(Character::isISOControl) || !input.token().equals(input.token().trim()))
            throw NotificationException.invalid();
        String hash = cipher.hash(input.token());
        String encrypted = cipher.encrypt(input.token());
        var owners = em.createNativeQuery("select user_id from native_fcm_devices where token_hash=:hash")
                .setParameter("hash", hash).getResultList();
        if (!owners.isEmpty() && ((Number) owners.getFirst()).longValue() != user.id())
            throw new NotificationException(409, "FCM_DEVICE_OWNED_BY_ANOTHER_USER");
        em.createNativeQuery("delete from native_fcm_devices where user_id=:user and token_hash=:hash and installation_id<>:installation")
                .setParameter("user", user.id()).setParameter("hash", hash).setParameter("installation", input.installationId()).executeUpdate();
        Number count = (Number) em.createNativeQuery("select count(*) from native_fcm_devices where user_id=:user and installation_id<>:installation")
                .setParameter("user", user.id()).setParameter("installation", input.installationId()).getSingleResult();
        if (count.longValue() >= 10) throw new NotificationException(429, "FCM_DEVICE_LIMIT");
        em.createNativeQuery("insert into native_fcm_devices (user_id,installation_id,token_hash,encrypted_token,platform) "
                + "values (:user,:installation,:hash,:encrypted,'ANDROID') "
                + "on conflict (user_id,installation_id) do update set "
                + "encrypted_token=case when native_fcm_devices.token_hash=excluded.token_hash "
                + "then native_fcm_devices.encrypted_token else excluded.encrypted_token end, "
                + "token_hash=excluded.token_hash,updated_at=current_timestamp")
                .setParameter("user", user.id()).setParameter("installation", input.installationId())
                .setParameter("hash", hash).setParameter("encrypted", encrypted).executeUpdate();
        return ApiResponse.success("OK", null);
    }

    @DeleteMapping("/{installationId}")
    @Transactional
    public ApiResponse<Void> unregister(@AuthenticationPrincipal LoginUser user, @PathVariable UUID installationId) {
        em.createNativeQuery("delete from native_fcm_devices where user_id=:user and installation_id=:installation")
                .setParameter("user", user.id()).setParameter("installation", installationId).executeUpdate();
        return ApiResponse.success("OK", null);
    }
}
