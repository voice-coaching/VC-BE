package org.example.voice.practicecontent;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practicecontent.application.PracticeContentService;
import org.example.voice.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PracticeContentRecommendationIntegrationTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired PracticeContentService service;
    @MockitoBean CacheManager cacheManager;
    private final RequestPostProcessor user = authentication(
            UsernamePasswordAuthenticationToken.authenticated(new LoginUser(42L), null, List.of()));
    private Long anchor;
    private Long first;

    @BeforeEach
    void setup() throws Exception {
        var caches = new ConcurrentMapCacheManager();
        when(cacheManager.getCache(anyString())).thenAnswer(call -> caches.getCache(call.getArgument(0)));
        em.createQuery("update PracticeContent c set c.status=:hidden").setParameter("hidden", PublishStatus.HIDDEN).executeUpdate();
        anchor = content(0, PublishStatus.PUBLISHED, Difficulty.BEGINNER).getId();
        for (int i = 1; i <= 7; i++) first = content(i, PublishStatus.PUBLISHED, Difficulty.BEGINNER).getId();
        content(9, PublishStatus.HIDDEN, Difficulty.BEGINNER);
        content(10, PublishStatus.PUBLISHED, Difficulty.ADVANCED);
        em.flush();
    }

    @Test
    void appliesDefaultAndRequestedCountsWithoutMixingCachedLimits() throws Exception {
        String path = "/api/practice-contents/" + anchor + "/recommendations";
        mvc.perform(get(path).with(user)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(5));
        mvc.perform(get(path).with(user).param("limit", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get(path).with(user).param("limit", "7")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(7));
        mvc.perform(get(path).with(user).param("limit", "20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(7));
        mvc.perform(get(path).with(user).param("limit", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void invalidLimitIs400AndMissingContentIs404() throws Exception {
        String path = "/api/practice-contents/" + anchor + "/recommendations";
        for (String limit : List.of("0", "-1", "abc", "1.5", "2147483648")) {
            mvc.perform(get(path).with(user).param("limit", limit)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.result").value(false));
        }
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/practice-contents/9223372036854775807/recommendations").with(user).param("limit", "1"))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> service.getPracticeContentRecommendations(anchor, 0)).isInstanceOf(BusinessException.class);
    }

    private PracticeContent content(int day, PublishStatus status, Difficulty difficulty) throws Exception {
        var constructor = PracticeContent.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var content = constructor.newInstance();
        var date = OffsetDateTime.parse("2026-01-01T00:00:00Z").plusDays(day);
        Map.of("contentType", ContentType.SENTENCE, "learningFocus", LearningFocus.BOTH,
                "title", "추천 테스트 문장", "scriptText", "또박또박 읽습니다.", "difficulty", difficulty,
                "status", status, "publishedAt", date, "createdAt", date, "updatedAt", date)
                .forEach((key, value) -> ReflectionTestUtils.setField(content, key, value));
        em.persist(content);
        return content;
    }
}
