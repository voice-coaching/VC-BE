package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import org.example.voice.admin.application.*;
import org.example.voice.admin.domain.model.AdminCommunicationData.*;
import org.example.voice.admin.domain.type.*;
import org.example.voice.common.security.LoginUser;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc @Transactional
class AdminCommunicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired AdminCommunicationService service;
    long actor,member,inactive;
    @BeforeEach void fixture() {
        actor=user(UserStatus.ACTIVE,UserRole.ADMIN);member=user(UserStatus.ACTIVE,UserRole.USER);inactive=user(UserStatus.SUSPENDED,UserRole.USER);em.flush();
    }
    @Test void permissionsUseCurrentDatabaseRoleForNewRoutes() throws Exception {
        mvc.perform(get("/api/admin/notification-campaigns")).andExpect(status().isUnauthorized());
        for(String path:List.of("/api/admin/notification-campaigns","/api/admin/popups","/api/admin/users/resolve?identifier="+member))
            mvc.perform(get(path).with(auth(member,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/resolve").param("identifier",Long.toString(member)).with(auth(actor,"ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].userId").value(member));
        mvc.perform(get("/api/admin/users/resolve").param("identifier","' or 1=1").with(auth(actor,"ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/{id}/records",member).param("resource","users;drop table users").with(auth(actor,"ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void selectedPlanNotificationIsDeliveredOnceAndAppearsInExistingInbox() throws Exception {
        var c=service.create(actor,new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.PLAN,"요금제 안내","새 이용권 안내입니다.","/mypage",List.of(member)),"테스트 안내");
        assertThat(c.audienceCount()).isEqualTo(1);assertThat(c.status()).isEqualTo("DRAFT");
        assertThat(inbox(member)).isZero();
        var sent=service.dispatch(actor,c.id(),c.version(),"확인 후 발송");
        assertThat(sent.status()).isEqualTo("SENT");assertThat(sent.sentCount()).isEqualTo(1);
        assertThat(service.dispatch(actor,c.id(),c.version(),"응답 유실 재시도").sentCount()).isEqualTo(1);
        assertThat(inbox(member)).isEqualTo(1);assertThat(inbox(actor)).isZero();
        mvc.perform(get("/api/notifications").with(auth(member,"USER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("ADMIN_PLAN")));
        assertThat(em.createQuery("select count(a) from AdminAuditLog a where a.targetId=:id and a.action='NOTIFICATION_DISPATCH'",Long.class)
                .setParameter("id",Long.toString(c.id())).getSingleResult()).isEqualTo(1);
    }
    @Test void allActiveAudienceIsEvaluatedAtSendTimeAndSkipsInactiveUsers() {
        var c=service.create(actor,new CampaignDraft(AdminAudience.ALL_ACTIVE,AdminNotificationKind.GENERAL,"서비스 안내","점검 안내입니다.","/notifications",List.of()),"등록");
        long joined=user(UserStatus.ACTIVE,UserRole.USER);em.flush();
        em.createQuery("update User u set u.status=:status where u.id=:id").setParameter("status",UserStatus.WITHDRAWN).setParameter("id",member).executeUpdate();
        var sent=service.dispatch(actor,c.id(),c.version(),"전체 발송");
        assertThat(inbox(actor)).isEqualTo(1);assertThat(inbox(joined)).isEqualTo(1);
        assertThat(inbox(member)).isZero();assertThat(inbox(inactive)).isZero();assertThat(sent.sentCount()).isGreaterThanOrEqualTo(2);
    }
    @Test void invalidAudienceAndLinksAreRejectedBeforeCreation() throws Exception {
        for(var d:List.of(
                new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.GENERAL,"제목","내용","/home",List.of(inactive)),
                new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.GENERAL,"제목","내용","/home",List.of(member,member)),
                new CampaignDraft(AdminAudience.ALL_ACTIVE,AdminNotificationKind.GENERAL,"제목","내용","/home",List.of(member)),
                new CampaignDraft(AdminAudience.ALL_ACTIVE,AdminNotificationKind.GENERAL,"제목","내용","https://outside.invalid",List.of())))
            assertThatThrownBy(()->service.create(actor,d,"등록")).isInstanceOf(org.example.voice.common.exception.BaseException.class);
        mvc.perform(post("/api/admin/notification-campaigns").with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":\"ALL_ACTIVE\",\"kind\":\"GENERAL\",\"title\":\"안내\",\"body\":\"내용\",\"deepLink\":\"/home\",\"reason\":\"등록\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void dispatchRejectsStaleDraftVersion() {
        var c=service.create(actor,new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.GENERAL,"제목","내용","/home",List.of(member)),"등록");
        assertThatThrownBy(()->service.dispatch(actor,c.id(),c.version()+1,"발송")).isInstanceOf(org.example.voice.common.exception.BaseException.class);
        assertThat(inbox(member)).isZero();
    }
    @Test void popupWindowPriorityAndDisableAreAppliedByServer() throws Exception {
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        var low=service.createPopup(actor,new PopupDraft("낮은 우선순위","내용",null,0,now.minusHours(1),2,true),"등록");
        var high=service.createPopup(actor,new PopupDraft("주요 안내","내용","/mypage",100,now.minusHours(1),3,true),"등록");
        service.createPopup(actor,new PopupDraft("미래 안내","내용",null,100,now.plusDays(1),3,true),"등록");
        service.createPopup(actor,new PopupDraft("종료 안내","내용",null,100,now.minusDays(2),1,true),"등록");
        mvc.perform(get("/api/users/me/popup").with(auth(member,"USER"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.id").value(high.id()));
        assertThat(high.endsAt()).isEqualTo(high.startsAt().plusDays(3));
        var disabled=service.editPopup(actor,high.id(),high.version(),new PopupDraft(high.title(),high.body(),high.deepLink(),100,high.startsAt(),3,false),"노출 중지");
        assertThat(disabled.version()).isGreaterThan(high.version());
        assertThat(service.activePopup(member).id()).isEqualTo(low.id());
        assertThatThrownBy(()->service.editPopup(actor,high.id(),high.version(),new PopupDraft("제목","내용",null,0,now,1,true),"재수정"))
                .isInstanceOf(org.example.voice.common.exception.BaseException.class);
    }
    @Test void popupRequiresLoginAndPositiveDuration() throws Exception {
        mvc.perform(get("/api/users/me/popup")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/popup").with(auth(inactive,"USER"))).andExpect(status().isForbidden());
        assertThat(service.activePopup(member)).isNull();
        assertThatThrownBy(()->service.createPopup(actor,new PopupDraft("안내","내용",null,0,OffsetDateTime.now(),0,true),"등록"))
                .isInstanceOf(org.example.voice.common.exception.BaseException.class);
    }
    @Test void deletedAccountCannotReadActivePopupEvenWithStaleToken() throws Exception {
        em.createQuery("update User u set u.deletedAt=:at where u.id=:id").setParameter("at",OffsetDateTime.now()).setParameter("id",member).executeUpdate();
        mvc.perform(get("/api/users/me/popup").with(auth(member,"USER"))).andExpect(status().isForbidden());
    }
    private long user(UserStatus status,UserRole role) {
        var user=User.createLocal(UUID.randomUUID()+"@example.invalid","test-only","member",OffsetDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(user,"status",status);ReflectionTestUtils.setField(user,"role",role);em.persist(user);return user.getId();
    }
    private long inbox(long id) { em.flush();return em.createQuery("select count(n) from UserNotification n where n.userId=:id",Long.class).setParameter("id",id).getSingleResult(); }
    private RequestPostProcessor auth(long id,String role) { return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
}
