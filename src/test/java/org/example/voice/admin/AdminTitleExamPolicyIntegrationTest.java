package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import org.example.voice.admin.application.*;
import org.example.voice.admin.infrastructure.AdminPersistence;
import org.example.voice.auth.application.TokenService;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.title.application.TitleService;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.*;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AdminTitleExamPolicyIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired AdminTitleExamPolicyService service;
    @Autowired AdminContentService contentService;
    @Autowired TitleService titleService;
    @MockitoSpyBean AdminPersistence store;
    @MockitoBean org.springframework.cache.CacheManager caches;
    TransactionTemplate tx;
    long actor, member, first, second, draft;
    String adminToken, memberToken;
    static final String PATH = "/api/admin/title-exam-policies";

    @BeforeEach void setup() {
        var local = new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(caches.getCache(anyString())).thenAnswer(call -> local.getCache(call.getArgument(0)));
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            var now = OffsetDateTime.now();
            var admin = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "시험 관리자", now);
            ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN); em.persist(admin); actor = admin.getId();
            var user = User.createLocal(UUID.randomUUID()+"@example.invalid", "hash", "시험 회원", now);
            em.persist(user); member = user.getId();
            first = content("기존 시험", PublishStatus.PUBLISHED, "TITLE_EXAM");
            second = content("새 시험", PublishStatus.PUBLISHED, "TITLE_EXAM");
            draft = content("미공개 시험", PublishStatus.DRAFT, "TITLE_EXAM");
            var policy = em.find(TitlePolicy.class, TitleRank.BEGINNER);
            if (policy == null) { policy = org.springframework.beans.BeanUtils.instantiateClass(TitlePolicy.class); ReflectionTestUtils.setField(policy, "targetRank", TitleRank.BEGINNER); em.persist(policy); }
            ReflectionTestUtils.setField(policy,"practiceContentId",first);
            ReflectionTestUtils.setField(policy,"passingScore",70);
            ReflectionTestUtils.setField(policy,"requiredTrainingCount",5L);
            ReflectionTestUtils.setField(policy,"editVersion",0L);
            em.flush();
            adminToken = tokens.issueSession(admin).accessToken(); memberToken = tokens.issueSession(user).accessToken();
        });
    }
    private long content(String title, PublishStatus status, String category) {
        var content = PracticeContent.draft(ContentType.SENTENCE, LearningFocus.PRONUNCIATION, category,
                Difficulty.BEGINNER, title, "시험 안내", "정확한 발음으로 문장을 읽습니다.", List.of(), 10, OffsetDateTime.now());
        if (status == PublishStatus.PUBLISHED) content.changePublication(status, OffsetDateTime.now());
        em.persist(content); em.flush(); return content.getId();
    }
    private String body(long version, long id, int score) { return "{\"contentVersion\":0,\"version\":"+version+",\"practiceContentId\":"+id+",\"passingScore\":"+score+",\"reason\":\"시험 문제 변경\"}"; }

    @Test void requiresSignedActiveAdministratorAndReturnsNoStore() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-User-Id", actor)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer "+memberToken)).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer "+adminToken)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.items[0].targetRank").value("BEGINNER"))
                .andExpect(jsonPath("$.data.items[0].available").value(true));
    }
    @Test void editIncrementsVersionAndAuditsWithoutChangingEligibilityCount() throws Exception {
        mvc.perform(patch(PATH+"/BEGINNER").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body(0,second,80))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1)).andExpect(jsonPath("$.data.passingScore").value(80))
                .andExpect(jsonPath("$.data.requiredTrainingCount").value(5)).andExpect(jsonPath("$.data.practiceContentId").value(second));
        tx.executeWithoutResult(s -> assertThat(em.createQuery("select count(a) from AdminAuditLog a where a.actorId=:actor and a.action='TITLE_EXAM_POLICY_EDIT'",Long.class).setParameter("actor",actor).getSingleResult()).isEqualTo(1));
    }
    @Test void rejectsStaleVersionAndInvalidRankOrScore() throws Exception {
        service.edit(actor,TitleRank.BEGINNER,0,second,0,80,"교체");
        mvc.perform(patch(PATH+"/BEGINNER").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(0,first,70)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_EDIT_CONFLICT"));
        for (int score : new int[]{0,101}) mvc.perform(patch(PATH+"/BEGINNER").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(1,first,score))).andExpect(status().isBadRequest());
        mvc.perform(patch(PATH+"/ABSOLUTE_BEGINNER").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(1,first,70))).andExpect(status().isBadRequest());
        mvc.perform(patch(PATH+"/NOT_A_RANK").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(1,first,70))).andExpect(status().isBadRequest());
    }
    @Test void rejectsDraftWrongCategoryAndMissingContent() throws Exception {
        long wrong = tx.execute(s -> content("일반 연습",PublishStatus.PUBLISHED,"FINAL_CONSONANT"));
        for (long id : new long[]{draft,wrong}) mvc.perform(patch(PATH+"/BEGINNER").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(0,id,80))).andExpect(status().isBadRequest());
        mvc.perform(patch(PATH+"/BEGINNER").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(0,Long.MAX_VALUE,80))).andExpect(status().isNotFound());
    }
    @Test void existingExamKeepsProblemAndThresholdAndNewExamUsesNewSettings() {
        var old = titleService.create(member,null);
        service.edit(actor,TitleRank.BEGINNER,0,second,0,80,"새 출제");
        var next = titleService.create(member,null);
        assertThat(titleService.get(member,old.id()).practiceContentId()).isEqualTo(first);
        assertThat(titleService.get(member,old.id()).passingScore()).isEqualTo(70);
        assertThat(next.practiceContentId()).isEqualTo(second); assertThat(next.passingScore()).isEqualTo(80);
    }
    @Test void changingCandidateAfterPreviewRejectsSave() throws Exception {
        tx.executeWithoutResult(s -> {
            var c = em.find(PracticeContent.class,second);
            ReflectionTestUtils.setField(c,"title","미리보기 이후 변경");
        });
        mvc.perform(patch(PATH+"/BEGINNER").header("Authorization","Bearer "+adminToken).contentType("application/json").content(body(0,second,80)))
                .andExpect(status().isConflict());
        assertThat(service.list(actor).getFirst().practiceContentId()).isEqualTo(first);
    }
    @Test void referencedProblemCannotBeHiddenOrDeletedButReplacementCanBePrepared() {
        var c = service.list(actor).getFirst().content();
        assertThatThrownBy(() -> contentService.delete(actor,first,c.version(),"삭제")).hasMessageContaining("승급 시험에서 사용 중");
        assertThatThrownBy(() -> contentService.publication(actor,first,c.version(),PublishStatus.HIDDEN,"숨김")).hasMessageContaining("승급 시험에서 사용 중");
        service.edit(actor,TitleRank.BEGINNER,0,second,0,80,"교체");
        assertThat(contentService.delete(actor,first,c.version(),"교체 후 정리").id()).isEqualTo(first);
    }
    @Test void inProgressOldExamStillProtectsProblemAfterPolicyReplacement() {
        titleService.create(member,null);
        var old = service.list(actor).getFirst().content();
        service.edit(actor,TitleRank.BEGINNER,0,second,0,80,"교체");
        assertThatThrownBy(() -> contentService.delete(actor,first,old.version(),"삭제")).isInstanceOf(org.example.voice.common.exception.BaseException.class);
    }
    @Test void failedAuditRollsBackPolicyAndVersion() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("audit unavailable"))
                .when(store).audit(argThat(log -> "TITLE_EXAM_POLICY_EDIT".equals(log.getAction())));
        assertThatThrownBy(() -> service.edit(actor,TitleRank.BEGINNER,0,second,0,80,"교체"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var policy = service.list(actor).getFirst();
        assertThat(policy.version()).isZero(); assertThat(policy.practiceContentId()).isEqualTo(first); assertThat(policy.passingScore()).isEqualTo(70);
    }
}
