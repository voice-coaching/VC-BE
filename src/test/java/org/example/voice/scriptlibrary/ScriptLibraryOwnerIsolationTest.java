package org.example.voice.scriptlibrary;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.scriptlibrary.domain.ScriptLibraryEntry;
import org.example.voice.scriptlibrary.domain.ScriptLibraryTopic;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@Transactional
class ScriptLibraryOwnerIsolationTest {
    private static final String PATH = "/api/users/me/script-library";
    @TestConfiguration
    static class CacheConfig {
        @Bean @Primary CacheManager isolationCache() { return new NoOpCacheManager(); }
    }
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    Long first, second, empty, firstContent, secondContent;

    @BeforeEach void seed() {
        first = user(); second = user(); empty = user();
        firstContent = script(first, "first private script", "first topic");
        secondContent = script(second, "second private script", "second topic");
        em.flush(); em.clear();
    }
    @Test void eachAuthenticatedUserSeesOnlyTheirOwnScriptAndTopic() throws Exception {
        expectLibrary(first, firstContent, "first private script", "first topic");
        expectLibrary(second, secondContent, "second private script", "second topic");
    }
    @Test void emptyLibraryDoesNotFallBackToOtherUsersScripts() throws Exception {
        mvc.perform(get(PATH).with(auth(empty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.topics").isEmpty())
                .andExpect(jsonPath("$.data.assignments").isEmpty());
    }
    @Test void anonymousRequestCannotReadAnyPrivateScript() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
    private void expectLibrary(Long user, Long content, String script, String topic) throws Exception {
        mvc.perform(get(PATH).with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(content.intValue()))
                .andExpect(jsonPath("$.data.items[0].scriptText").value(script))
                .andExpect(jsonPath("$.data.topics.length()").value(1))
                .andExpect(jsonPath("$.data.topics[0].name").value(topic))
                .andExpect(jsonPath("$.data.assignments.size()").value(1))
                .andExpect(jsonPath("$.data.assignments['" + content + "']").value(topic));
    }
    private Long user() {
        var user = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "owner", OffsetDateTime.now());
        em.persist(user); return user.getId();
    }
    private Long script(Long user, String text, String name) {
        var topic = new ScriptLibraryTopic(user, name, UUID.randomUUID().toString());
        em.persist(topic);
        var content = PracticeContent.custom(user, "private title", text, LearningFocus.PRONUNCIATION, OffsetDateTime.now());
        em.persist(content);
        em.persist(new ScriptLibraryEntry(content.getId(), user, topic.getId(), OffsetDateTime.now()));
        return content.getId();
    }
    private RequestPostProcessor auth(Long user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(user), null, List.of()));
    }
}
