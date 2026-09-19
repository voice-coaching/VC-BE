package org.example.voice.profileimage;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.profileimage.application.ProfileImageService;
import org.example.voice.profileimage.application.ProfileImageTransactions;
import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.domain.port.ProfileImageStorage;
import org.example.voice.profileimage.infrastructure.ProfileImageCleanup;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "profile-image.cleanup-delay-ms=3600000")
@AutoConfigureMockMvc
class ProfileImageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ProfileImageService images;
    @Autowired ProfileImageTransactions transactions;
    @Autowired UserService users;
    @Autowired ProfileImageCleanup cleanup;
    @Autowired ObjectMapper json;
    @MockitoBean ProfileImageStorage storage;
    @Autowired TestClock clock;
    Long owner, other;
    byte[] png;
    private final java.time.Instant instant = java.time.Instant.parse("2026-09-16T00:00:00Z");
    @BeforeEach void setup() throws Exception {
        clock.time = instant;
        when(storage.imageUrl(anyString())).thenAnswer(i -> "https://cdn.example.invalid/" + i.getArgument(0));
        doAnswer(i -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return null; })
                .when(storage).upload(anyString(), any());
        var ids = new TransactionTemplate(manager).execute(s -> {
            var first = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "photo owner", OffsetDateTime.now(clock));
            var second = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "photo other", OffsetDateTime.now(clock));
            em.persist(first); em.persist(second); em.flush(); return List.of(first.getId(), second.getId());
        });
        owner = ids.get(0); other = ids.get(1); png = ImageIoProfileImageProcessorTest.image(128, 256, "png");
    }
    @Test void crudAndReplayUseAuthenticatedOwnerAndExposeUrlInProfile() throws Exception {
        mvc.perform(get(path()).with(as(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        String first = mvc.perform(multipart(path()).file(file()).header("Idempotency-Key", "once").with(as(owner)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        String repeated = mvc.perform(multipart(path()).file(file()).header("Idempotency-Key", "once").with(as(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(repeated).isEqualTo(first);
        String url = json.readTree(first).path("data").path("imageUrl").asText();
        mvc.perform(get("/api/users/me").with(as(owner))).andExpect(jsonPath("$.data.profileImageUrl").value(url));
        mvc.perform(get(path()).with(as(other))).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(multipart(path()).file(file()).with(as(owner))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_EXISTS"));
        mvc.perform(multipart(path()).file(new MockMultipartFile("file", "changed.png", "image/png", png))
                        .header("Idempotency-Key", "once").with(as(owner)))
                .andExpect(status().isConflict());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, path()).file(file()).with(as(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_IMAGE_NOT_FOUND"));
        String replaced = mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, path()).file(file()).with(as(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replaced).path("data").path("imageUrl").asText()).isNotEqualTo(url);
        for (int i=0; i<2; i++) mvc.perform(delete(path()).with(as(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").with(as(owner))).andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
        clock.time = instant.plusSeconds(601); cleanup.clean();
        verify(storage, times(2)).delete(anyString());
    }
    @Test void malformedAndUnauthorizedRequestsDoNotUpload() throws Exception {
        mvc.perform(multipart(path()).file(file())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path()).file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())).with(as(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROFILE_IMAGE"));
        mvc.perform(multipart(path()).with(as(owner))).andExpect(status().isBadRequest());
        mvc.perform(multipart(path()).file(file()).header("Idempotency-Key", " ").with(as(owner))).andExpect(status().isBadRequest());
        verify(storage, never()).upload(anyString(), any());
    }
    @Test void storageFailureLeavesDurableCleanupAndNoVisiblePhoto() {
        doThrow(ProfileImageException.unavailable()).when(storage).upload(anyString(), any());
        assertThatThrownBy(() -> images.upload(owner, false, png, "a.png", null)).isInstanceOf(ProfileImageException.class);
        assertThat(images.current(owner)).isNull();
        clock.time = instant.plusSeconds(601); cleanup.clean();
        verify(storage).delete(anyString());
    }
    @Test void withdrawalCancelsOutstandingUploadAndRetiresActivePhoto() {
        images.upload(owner, false, png, "a.png", null);
        users.withdraw(owner);
        assertThatThrownBy(() -> images.current(owner)).isInstanceOf(ProfileImageException.class);
        clock.time = instant.plusSeconds(601); cleanup.clean();
        verify(storage).delete(anyString());
    }
    @Test void concurrentCreatesPublishOnlyOneImageAndCleanupLosingUpload() throws Exception {
        var entered = new CountDownLatch(2);
        doAnswer(i -> { entered.countDown(); assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue(); return null; })
                .when(storage).upload(anyString(), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> create = () -> { try { images.upload(owner, false, png, "a.png", null); return 201; }
                catch (ProfileImageException e) { return e.status(); } };
            var a = pool.submit(create); var b = pool.submit(create);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(images.current(owner)).isNotNull();
        clock.time = instant.plusSeconds(601); cleanup.clean();
        verify(storage).delete(anyString());
    }
    @Test void withdrawalDuringUploadPreventsLateActivation() throws Exception {
        var uploading = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        doAnswer(i -> { uploading.countDown(); assertThat(finish.await(10, TimeUnit.SECONDS)).isTrue(); return null; })
                .when(storage).upload(anyString(), any());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> images.upload(owner, false, png, "a.png", null));
            try {
                assertThat(uploading.await(10, TimeUnit.SECONDS)).isTrue();
                users.withdraw(owner);
            } finally { finish.countDown(); }
            assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ProfileImageException.class);
        }
        clock.time = instant.plusSeconds(601); cleanup.clean();
        verify(storage).delete(anyString());
    }
    @Test void failedDeletionIsRetriedAndActivePhotoIsNeverDeleted() {
        images.upload(owner, false, png, "a.png", null);
        cleanup.clean(); verify(storage, never()).delete(anyString());
        images.delete(owner);
        clock.time = instant.plusSeconds(601);
        doThrow(ProfileImageException.unavailable()).doNothing().when(storage).delete(anyString());
        cleanup.clean();
        clock.time = instant.plusSeconds(662); cleanup.clean();
        verify(storage, times(2)).delete(anyString());
        clock.time = instant.plusSeconds(1000); cleanup.clean();
        verify(storage, times(2)).delete(anyString());
    }
    private String path() { return "/api/users/me/profile-image"; }
    private MockMultipartFile file() { return new MockMultipartFile("file", "photo.png", "application/octet-stream", png); }
    private RequestPostProcessor as(Long id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id), null, List.of()));
    }
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary TestClock profileTestClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        volatile java.time.Instant time = java.time.Instant.parse("2026-09-16T00:00:00Z");
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(time, zone); }
        public java.time.Instant instant() { return time; }
    }
}
