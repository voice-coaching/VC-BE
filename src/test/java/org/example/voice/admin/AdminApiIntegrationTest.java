package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import org.example.voice.admin.application.*;
import org.example.voice.admin.domain.model.AdminData.ContentDraft;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.admin.infrastructure.AdminPersistence;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.payment.domain.entity.TestMembershipGrant;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practicecontent.domain.port.ContentCatalogReader;
import org.example.voice.practicecontent.domain.model.ContentCatalogData.Filter;
import org.example.voice.support.application.SupportService;
import org.example.voice.support.domain.model.SupportData.InquiryDraft;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc @Transactional
class AdminApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired org.example.voice.auth.application.TokenService tokenService;
    @Autowired org.example.voice.auth.domain.port.TokenProvider tokenProvider;
    @Autowired ObjectMapper json;
    @Autowired AdminContentService contents;
    @Autowired AdminReadService reads;
    @Autowired SupportService support;
    @Autowired ContentCatalogReader catalog;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AdminPersistence store;
    long actor,owner;
    static final OffsetDateTime NOW=OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach void fixture() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var a=User.createLocal(UUID.randomUUID()+"@example.invalid","not-a-real-password","operator",NOW);
            ReflectionTestUtils.setField(a,"role",UserRole.ADMIN);em.persist(a);
            var u=User.createLocal(UUID.randomUUID()+"@example.invalid","not-a-real-password","member",NOW);em.persist(u);
            em.flush();actor=a.getId();owner=u.getId();
            em.persist(TestMembershipGrant.create(owner,NOW));
            if(em.createQuery("select count(c) from ContentCategory c where c.contentType=:type and c.code='FINAL_CONSONANT'",Long.class)
                    .setParameter("type",ContentType.SENTENCE).getSingleResult()==0) {
                var category=org.springframework.beans.BeanUtils.instantiateClass(ContentCategory.class);ReflectionTestUtils.setField(category,"contentType",ContentType.SENTENCE);
                ReflectionTestUtils.setField(category,"code","FINAL_CONSONANT");ReflectionTestUtils.setField(category,"label","받침");
                ReflectionTestUtils.setField(category,"sortOrder",1);em.persist(category);
            }
            em.flush();
        });
    }
    @Test void requestSelectorsAndUnsignedTokensCannotAuthorizeAdministrator() throws Exception {
        mvc.perform(get("/api/admin/me").header("X-User-Id",actor)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/me").header("Authorization","Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0."))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/me").with(auth(owner,"USER")).header("X-User-Id",actor))
                .andExpect(status().isForbidden());
    }
    @Test void revokedSessionCannotAccessAdministratorApi() throws Exception {
        var issued = tokenService.issueSession(em.find(User.class,actor));
        tokenService.revoke(actor,null);
        em.flush();
        mvc.perform(get("/api/admin/me").header("Authorization","Bearer "+issued.accessToken()))
                .andExpect(status().isUnauthorized());
    }
    @Test void currentDatabaseRoleAndStatusAreRequiredEvenWithAdminToken() throws Exception {
        mvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/me").with(auth(owner,"USER"))).andExpect(status().isForbidden());
        // Test membership admin does not grant actual administrator rights, even with a stale/spoofed authority.
        mvc.perform(get("/api/admin/me").with(auth(owner,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/me").with(auth(actor,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
        em.createQuery("update User u set u.role=:role where u.id=:id").setParameter("role",UserRole.USER).setParameter("id",actor).executeUpdate();
        mvc.perform(get("/api/admin/me").with(auth(actor,"ADMIN"))).andExpect(status().isForbidden());
        em.createQuery("update User u set u.role=:role,u.status=:status where u.id=:id").setParameter("role",UserRole.ADMIN)
                .setParameter("status",UserStatus.SUSPENDED).setParameter("id",actor).executeUpdate();
        mvc.perform(get("/api/admin/me").with(auth(actor,"ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void createsEditsPublishesAndHidesContentWithoutMutatingPublishedScript() throws Exception {
        var draft=contents.create(actor,draft("admin-content-flow"),"테스트 등록");
        assertThat(catalog.list(new Filter(ContentType.SENTENCE,"FINAL_CONSONANT",null,null),0,100).items())
                .noneMatch(c -> c.id().equals(draft.id()));
        var edited=contents.edit(actor,draft.id(),draft.version(),draft("admin-content-edited"),"초안 수정");
        assertThat(edited.version()).isGreaterThan(draft.version());
        mvc.perform(patch("/api/admin/contents/{id}",draft.id()).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(contentEdit(draft.version()))).andExpect(status().isConflict());
        var published=contents.publication(actor,draft.id(),edited.version(),PublishStatus.PUBLISHED,"공개");
        assertThat(catalog.list(new Filter(ContentType.SENTENCE,"FINAL_CONSONANT",null,null),0,100).items())
                .anyMatch(c -> c.id().equals(draft.id()));
        assertThatThrownBy(() -> contents.edit(actor,draft.id(),published.version(),draft("forbidden"),"원문 변경"))
                .hasMessageContaining("수정할 수 없습니다");
    }
    @Test void publishedThenHiddenContentRemainsImmutableAndPrivateContentIsInaccessible() throws Exception {
        var d=contents.create(actor,draft("hidden-original"),"등록");
        var p=contents.publication(actor,d.id(),d.version(),PublishStatus.PUBLISHED,"공개");
        var h=contents.publication(actor,p.id(),p.version(),PublishStatus.HIDDEN,"숨김");
        assertThat(h.publishedAt()).isEqualTo(p.publishedAt());
        mvc.perform(patch("/api/admin/contents/{id}",h.id()).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(contentEdit(h.version()))).andExpect(status().isConflict());
        var privateContent=PracticeContent.custom(owner,"private title","private script",LearningFocus.PRONUNCIATION,NOW);em.persist(privateContent);em.flush();
        mvc.perform(get("/api/admin/contents/{id}",privateContent.getId()).with(auth(actor,"ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/contents/{id}/publication",privateContent.getId()).with(auth(actor,"ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"status\":\"PUBLISHED\",\"reason\":\"test\"}"))
                .andExpect(status().isNotFound());
    }
    @Test void noticeHiddenAndScheduledVisibilityMatchesUserApi() throws Exception {
        String response=mvc.perform(post("/api/admin/notices").with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(notice(false,NOW.minusDays(1),null))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var data=json.readTree(response).path("data");long id=data.path("id").asLong();
        mvc.perform(get("/api/notices/{id}",id).with(auth(owner,"USER"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/notices/{id}",id).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(notice(true,NOW.plusDays(1),data.path("version").asLong()))).andExpect(status().isOk());
        mvc.perform(get("/api/notices/{id}",id).with(auth(owner,"USER"))).andExpect(status().isNotFound());
        long version=store.notice(id).orElseThrow().version();
        mvc.perform(patch("/api/admin/notices/{id}",id).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(notice(true,NOW.minusDays(1),version))).andExpect(status().isOk());
        mvc.perform(get("/api/notices/{id}",id).with(auth(owner,"USER"))).andExpect(status().isOk());
    }
    @Test void inquiryAnswerIsVisibleToOwnerAndAuditNeverContainsTheAnswer() throws Exception {
        var inquiry=support.create(owner,new InquiryDraft("ANALYSIS","help","private question",null,null),null);
        String answer="private answer text";
        mvc.perform(patch("/api/admin/inquiries/{id}/answer",inquiry.id()).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version",0,"answer",answer,"reason","답변 처리"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ANSWERED"));
        assertThat(support.inquiry(owner,inquiry.id()).answer()).isEqualTo(answer);
        mvc.perform(get("/api/admin/inquiries").with(auth(actor,"ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].body").doesNotExist()).andExpect(jsonPath("$.data.items[0].replyEmail").doesNotExist());
        var record=store.inquiry(inquiry.id()).orElseThrow();
        mvc.perform(patch("/api/admin/inquiries/{id}/status",inquiry.id()).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version",record.version(),"status","CLOSED","reason","처리 완료")))).andExpect(status().isOk());
        String audit=mvc.perform(get("/api/admin/audit-logs").param("targetType","INQUIRY").param("targetId",inquiry.id().toString())
                .with(auth(actor,"ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(audit).doesNotContain(answer,"private question").contains("INQUIRY_ANSWER","INQUIRY_STATUS");
    }
    @Test void userResponsesAreMaskedAndQueriesValidateTypesBoundsAndLiteralWildcards() throws Exception {
        mvc.perform(get("/api/admin/users/{id}",owner).with(auth(actor,"ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedEmail").value(org.hamcrest.Matchers.containsString("***")))
                .andExpect(jsonPath("$.data.membershipType").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.loginProviders[0]").value("LOCAL"));
        mvc.perform(get("/api/admin/users").param("keyword","%").with(auth(actor,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/admin/contents").param("type","INVALID").with(auth(actor,"ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/contents").param("size","101").with(auth(actor,"ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/content-categories").param("type","SENTENCE").with(auth(actor,"ADMIN"))).andExpect(status().isOk());
    }
    @Test void createAndEditPayloadsValidateInsteadOfAcceptingMassAssignment() throws Exception {
        mvc.perform(post("/api/admin/contents").with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/notices").with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notice\":{\"title\":\"x\",\"summary\":\"x\",\"sections\":[],\"publishedAt\":\"2026-01-01T00:00:00Z\"},\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackBusinessMutation() {
        String title="rollback-"+UUID.randomUUID();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("audit unavailable"))
                .when(store).audit(any(AdminAuditLog.class));
        assertThatThrownBy(() -> contents.create(actor,draft(title),"등록")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> assertThat(em.createQuery("select count(c) from PracticeContent c where c.title=:title",Long.class)
                .setParameter("title",title).getSingleResult()).isZero());
    }
    private ContentDraft draft(String title) { return new ContentDraft(ContentType.SENTENCE,LearningFocus.PRONUNCIATION,"FINAL_CONSONANT",Difficulty.BEGINNER,title,"설명","밤이 되면 밥을 먹습니다.",List.of("밤","밥"),8); }
    private String contentEdit(long version) throws Exception {
        return json.writeValueAsString(Map.of("version",version,"reason","수정","content",Map.of("type","SENTENCE","focus","PRONUNCIATION","category","FINAL_CONSONANT",
                "difficulty","BEGINNER","title","updated","description","desc","scriptText","다른 원문입니다.","targetPronunciations",List.of("원문"),"estimatedSeconds",8)));
    }
    private String notice(boolean published,OffsetDateTime at,Long version) throws Exception {
        var data=new LinkedHashMap<String,Object>();data.put("reason","공지 변경");if(version!=null)data.put("version",version);
        data.put("notice",Map.of("title","운영 공지","summary","요약","sections",List.of(Map.of("title","안내","paragraphs",List.of("본문"))),"pinned",false,"published",published,"publishedAt",at.toString()));
        return json.writeValueAsString(data);
    }
    private RequestPostProcessor auth(long id,String role) {
        var issued = tokenService.issueSession(em.find(User.class,id));
        String token = tokenProvider.reissue(id, UserRole.valueOf(role), issued.sessionId()).accessToken();
        return request -> { request.addHeader("Authorization", "Bearer " + token); return request; };
    }
}
