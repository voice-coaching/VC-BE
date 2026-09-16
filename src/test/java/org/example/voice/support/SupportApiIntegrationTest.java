package org.example.voice.support;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.support.domain.entity.Notice;
import org.example.voice.support.domain.model.SupportData.Section;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupportApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    Long ownerId;
    Long otherId;
    private static final String DRAFT = """
            {"category":"ANALYSIS","subject":"분석 문의","body":"억양 결과를 확인하고 싶어요.",
             "replyEmail":"support-test@example.invalid"}
            """;

    @BeforeEach void users() {
        var now = OffsetDateTime.now(clock);
        User owner = User.createLocal("support-owner@example.invalid", "encoded", "문의 사용자", now);
        User other = User.createLocal("support-other@example.invalid", "encoded", "다른 사용자", now);
        em.persist(owner); em.persist(other); em.flush();
        ownerId = owner.getId(); otherId = other.getId();
    }

    @Test void noticesHideFutureAndHiddenEntriesAndSortPinnedFirst() throws Exception {
        var now = OffsetDateTime.now(clock);
        var sections = List.of(new Section("안내", List.of("서비스 이용 안내입니다.")));
        Notice normal = Notice.scheduled("일반", "요약", sections, false, now.minusHours(1));
        Notice pinned = Notice.scheduled("고정", "요약", sections, true, now.minusDays(2));
        Notice future = Notice.scheduled("예약", "요약", sections, true, now.plusDays(1));
        Notice hidden = Notice.scheduled("숨김", "요약", sections, true, now.minusHours(1));
        hidden.hide();
        for (Notice n : List.of(normal, pinned, future, hidden)) em.persist(n);
        em.flush(); em.clear();
        mvc.perform(get("/api/notices?size=1").with(as(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(pinned.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/notices?page=1&size=1").with(as(ownerId)))
                .andExpect(jsonPath("$.data.items[0].id").value(normal.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(get("/api/notices/{id}", normal.getId()).with(as(ownerId)))
                .andExpect(jsonPath("$.data.sections[0].paragraphs[0]").value("서비스 이용 안내입니다."));
        for (Long id : List.of(future.getId(), hidden.getId(), Long.MAX_VALUE)) {
            mvc.perform(get("/api/notices/{id}", id).with(as(ownerId)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Test void inquiryReplayIsStableAndDifferentBodyConflicts() throws Exception {
        String first = mvc.perform(post("/api/inquiries").with(as(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "inquiry-once").content(DRAFT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(post("/api/inquiries").with(as(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "inquiry-once").content(DRAFT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        Long id = json.readTree(first).path("data").path("id").asLong();
        mvc.perform(get("/api/users/me/inquiries/{id}", id).with(as(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.subject").value("분석 문의"));
        mvc.perform(post("/api/inquiries").with(as(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "inquiry-once").content(DRAFT.replace("분석 문의", "다른 문의")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(em.createQuery("select count(i) from Inquiry i where i.userId=:owner", Long.class)
                .setParameter("owner", ownerId).getSingleResult()).isEqualTo(1);
    }

    @Test void inquiriesAreIsolatedByOwnerIncludingIdempotencyKeys() throws Exception {
        String response = mvc.perform(post("/api/inquiries").with(as(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key").content(DRAFT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).path("data").path("id").asLong();
        mvc.perform(get("/api/users/me/inquiries/{id}", id).with(as(otherId)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/users/me/inquiries").with(as(otherId)))
                .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(post("/api/inquiries").with(as(otherId)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key").content(DRAFT))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/users/me/inquiries").with(as(ownerId)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test void invalidInputsAndMissingRelatedSessionDoNotCreateInquiries() throws Exception {
        for (String body : List.of(DRAFT.replace("분석 문의", " "), DRAFT.replace("분석 문의", "가".repeat(101)),
                DRAFT.replace("support-test@example.invalid", "bad-address"), "{")) {
            mvc.perform(post("/api/inquiries").with(as(ownerId)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post("/api/inquiries").with(as(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT.replace("\"category\"", "\"relatedSessionId\":999999,\"category\"")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notices?size=101").with(as(ownerId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/notices?page=bad").with(as(ownerId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(em.createQuery("select count(i) from Inquiry i where i.userId=:owner", Long.class)
                .setParameter("owner", ownerId).getSingleResult()).isZero();
    }

    @Test void authenticationAndBrowserIdempotencyHeaderAreSupported() throws Exception {
        mvc.perform(get("/api/notices")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(options("/api/inquiries").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor as(Long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, List.of()));
    }
}
