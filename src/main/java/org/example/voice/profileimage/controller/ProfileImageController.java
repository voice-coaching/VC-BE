package org.example.voice.profileimage.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.profileimage.application.ProfileImageService;
import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.domain.model.ProfileImageData;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.OffsetDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/profile-image")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_PROFILE_IMAGE 또는 VALIDATION_ERROR"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PROFILE_IMAGE_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PROFILE_IMAGE_EXISTS 또는 CONFLICT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "TEMPORARY_UNAVAILABLE")
})
public class ProfileImageController {
    private final ProfileImageService service;
    public record ProfileImageResponse(Long id, String imageUrl, String originalFileName, String mimeType,
                                       long sizeBytes, OffsetDateTime updatedAt) {
        static ProfileImageResponse from(ProfileImageData data) {
            return data == null ? null : new ProfileImageResponse(data.id(), data.imageUrl(), data.originalFileName(),
                    data.mimeType(), data.sizeBytes(), data.updatedAt());
        }
    }
    @GetMapping
    public ApiResponse<ProfileImageResponse> get(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("프로필 사진을 조회했습니다.", ProfileImageResponse.from(service.current(user.id())));
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileImageResponse> create(@AuthenticationPrincipal LoginUser user,
            @RequestPart("file") MultipartFile file,
            @Parameter(schema = @Schema(maxLength = 128)) @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.success("프로필 사진을 등록했습니다.", ProfileImageResponse.from(service.upload(user.id(), false,
                bytes(file), file.getOriginalFilename(), key)));
    }
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileImageResponse> replace(@AuthenticationPrincipal LoginUser user, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success("프로필 사진을 교체했습니다.", ProfileImageResponse.from(service.upload(user.id(), true,
                bytes(file), file.getOriginalFilename(), null)));
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal LoginUser user) { service.delete(user.id()); }
    private byte[] bytes(MultipartFile file) {
        if (file.getSize() > 5 * 1024 * 1024) throw new ProfileImageException(413, "PAYLOAD_TOO_LARGE");
        try (var input = file.getInputStream()) { return input.readNBytes(5 * 1024 * 1024 + 1); }
        catch (IOException ignored) { throw ProfileImageException.invalid(); }
    }
}
