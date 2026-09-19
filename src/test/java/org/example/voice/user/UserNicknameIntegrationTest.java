package org.example.voice.user;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserNicknameIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @Test
    void duplicateDisplayNamesPreserveIdsAndAuthenticatedProfileOwnership() throws Exception {
        var first = user("first");
        var second = user("shared");
        Long firstId = first.getId(), secondId = second.getId();

        mvc.perform(patch("/api/users/me").with(as(firstId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  shared  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("shared"));
        em.flush();
        em.clear();
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(em.find(User.class, firstId).getNickname()).isEqualTo("shared");
        assertThat(em.find(User.class, secondId).getNickname()).isEqualTo("shared");
        mvc.perform(get("/api/users/me").with(as(firstId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(firstId));
        mvc.perform(get("/api/users/me").with(as(secondId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(secondId));

        mvc.perform(patch("/api/users/me").with(as(firstId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"changed\"}"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(em.find(User.class, firstId).getNickname()).isEqualTo("changed");
        assertThat(em.find(User.class, secondId).getNickname()).isEqualTo("shared");
    }

    @Test
    void unauthenticatedRenameCannotModifyAnExistingProfile() throws Exception {
        Long id = user("unchanged").getId();
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"changed\"}"))
                .andExpect(status().isUnauthorized());
        em.flush();
        em.clear();
        assertThat(em.find(User.class, id).getNickname()).isEqualTo("unchanged");
    }

    private User user(String nickname) {
        var user = User.createLocal(UUID.randomUUID() + "@example.invalid", "test-hash", nickname, OffsetDateTime.now());
        em.persist(user);
        em.flush();
        return user;
    }

    private RequestPostProcessor as(Long id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id), null, List.of()));
    }
}
