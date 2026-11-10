package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.example.voice.admin.application.*;
import org.example.voice.admin.domain.model.AdminCommunicationData.*;
import org.example.voice.admin.domain.port.*;
import org.example.voice.admin.domain.entity.AdminPopupImage;
import org.example.voice.admin.domain.type.PopupImageState;
import org.example.voice.auth.application.TokenService;
import org.example.voice.common.exception.BaseException;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","popup-image.cleanup-delay-ms=86400000"})
@AutoConfigureMockMvc
class PopupImageIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired AdminCommunicationService popups;
    @Autowired PopupImageService service;
    @Autowired PopupImageTransactions transactions;
    @Autowired PopupImageStore images;
    @Autowired PopupImageProcessor processor;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @MockitoBean PopupImageStorage storage;
    @MockitoBean Clock clock;
    @MockitoBean org.springframework.cache.CacheManager caches;
    TransactionTemplate tx;
    AtomicReference<Instant> time;
    long actor,member;
    String token,userToken;
    byte[] png;
    @BeforeEach void setup() throws Exception {
        time=new AtomicReference<>(Instant.parse("2026-11-10T04:00:00Z"));
        when(clock.instant()).thenAnswer(i->time.get());when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var local=new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(caches.getCache(anyString())).thenAnswer(i->local.getCache(i.getArgument(0)));
        when(storage.url(anyString(),any())).thenReturn("https://images.example.invalid/popup.png");
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(s->{
            var admin=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","admin",now());admin.changeRole(UserRole.ADMIN,now());em.persist(admin);actor=admin.getId();
            var user=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","member",now());em.persist(user);member=user.getId();
            token=tokens.issueSession(admin).accessToken();userToken=tokens.issueSession(user).accessToken();
        });
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(16,10,BufferedImage.TYPE_INT_RGB),"png",output);png=output.toByteArray();
    }
    private OffsetDateTime now(){return time.get().atOffset(ZoneOffset.UTC);}
    private PopupView create(){return popups.createPopup(actor,new PopupDraft("이미지 공지 "+actor,"본문 유지",null,100,now().minusMinutes(1),2,true),"등록");}
    private PopupView attach(PopupView p){service.upload(actor,p.id(),p.version(),"이미지 첨부",png);return popups.popup(actor,p.id());}
    @Test void multipartUploadUsesCurrentAdminAndVersionAndAppearsInActivePopup() throws Exception {
        var p=create();
        mvc.perform(multipart("/api/admin/popups/"+p.id()+"/image").file(new MockMultipartFile("file","photo.png","image/png",png))
                .param("version",String.valueOf(p.version())).param("reason","첨부").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.version").value(p.version()+1)).andExpect(jsonPath("$.data.image.url").value("https://images.example.invalid/popup.png"));
        assertThat(popups.activePopup(member).image()).isNotNull();
        verify(storage).upload(matches("popup-images/[0-9a-f-]{36}\\.png"),any());
        assertThat(popups.popup(actor,p.id()).body()).isEqualTo("본문 유지");
    }
    @Test void rejectsInvalidBytesStaleVersionAndUnauthenticatedUpload() throws Exception {
        var p=create();
        assertThatThrownBy(()->service.upload(actor,p.id(),p.version(),"attach","<svg/>".getBytes())).isInstanceOf(BaseException.class);
        mvc.perform(multipart("/api/admin/popups/"+p.id()+"/image").file(new MockMultipartFile("file","x.png","image/png",png))
                .param("version","0").param("reason","첨부")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/popups/"+p.id()+"/image").file(new MockMultipartFile("file","x.png","image/png",png))
                .param("version","0").param("reason","첨부").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/popups/"+p.id()+"/image").file(new MockMultipartFile("file","x.png","image/png",png))
                .param("version","-1").param("reason","첨부").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        attach(p);
        assertThatThrownBy(()->service.upload(actor,p.id(),p.version(),"old",png)).isInstanceOf(BaseException.class);
        verify(storage,times(1)).upload(anyString(),any());
    }
    @Test void replacementAndDetachRetireOnlyThePreviousObjects() {
        var original=attach(create());var first=original.image().id();
        var replacement=attach(original);var second=replacement.image().id();
        transactions.cleanup(first);
        verify(storage).delete("popup-images/"+first+".png");
        assertThat(popups.popup(actor,replacement.id()).image().id()).isEqualTo(second);
        transactions.remove(actor,replacement.id(),replacement.version(),"해제");transactions.cleanup(second);
        assertThat(popups.popup(actor,replacement.id()).image()).isNull();
        assertThat(popups.popup(actor,replacement.id()).body()).isEqualTo("본문 유지");
    }
    @Test void expirationHidesImageAndExtensionPreventsDeletionEvenForPreviouslySelectedCandidate() {
        var p=attach(create());var id=p.image().id();
        time.set(p.endsAt().toInstant());
        assertThat(popups.popup(actor,p.id()).image()).isNull();
        assertThat(transactions.due()).contains(id);
        var extended=popups.editPopup(actor,p.id(),p.version(),new PopupDraft(p.title(),p.body(),p.deepLink(),p.priority(),p.startsAt(),5,true),"기간 연장");
        transactions.cleanup(id);verify(storage,never()).delete(anyString());
        assertThat(extended.image().expiresAt()).isEqualTo(extended.endsAt());
        time.set(extended.endsAt().toInstant());transactions.cleanup(id);
        verify(storage).delete("popup-images/"+id+".png");
    }
    @Test void failedDeletionRemainsRetryableAndDoesNotRestoreExpiredImage() {
        var p=attach(create());var id=p.image().id();time.set(p.endsAt().toInstant());
        doThrow(new IllegalStateException("storage offline")).when(storage).delete(anyString());
        assertThatThrownBy(()->transactions.cleanup(id)).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.due()).contains(id);assertThat(popups.popup(actor,p.id()).image()).isNull();
        doNothing().when(storage).delete(anyString());transactions.cleanup(id);
        PopupImageState state=tx.execute(s->images.find(id).orElseThrow().getState());
        assertThat(state).isEqualTo(PopupImageState.DELETED);
    }
    @Test void interruptedUploadAndCommitConflictKeepDurableCleanupRecords() {
        var p=create();var reserved=transactions.reserve(actor,p.id(),p.version(),"첨부");
        var updated=popups.editPopup(actor,p.id(),p.version(),new PopupDraft(p.title(),p.body(),null,1,p.startsAt(),2,true),"변경");
        assertThatThrownBy(()->transactions.attach(actor,p.id(),p.version(),reserved.id(),"오래된 첨부")).isInstanceOf(BaseException.class);
        time.set(time.get().plus(Duration.ofHours(25)));transactions.cleanup(reserved.id());
        verify(storage).delete(reserved.objectKey());assertThat(popups.popup(actor,p.id()).version()).isEqualTo(updated.version());
    }
    @Test void periodEditDuringUploadCannotBeOverwrittenByTheEarlierRequestSnapshot() throws Exception {
        var p=create();
        doAnswer(invocation->{
            try(var workers=java.util.concurrent.Executors.newSingleThreadExecutor()) {
                workers.submit(()->popups.editPopup(actor,p.id(),p.version(),new PopupDraft(p.title(),"other administrator edit",null,1,p.startsAt(),5,true),"concurrent edit"))
                        .get(15,java.util.concurrent.TimeUnit.SECONDS);
            }
            return null;
        }).when(storage).upload(anyString(),any());
        mvc.perform(multipart("/api/admin/popups/"+p.id()+"/image").file(new MockMultipartFile("file","x.png","image/png",png))
                .param("version",String.valueOf(p.version())).param("reason","attach").header("Authorization","Bearer "+token))
                .andExpect(status().isConflict());
        var saved=popups.popup(actor,p.id());assertThat(saved.version()).isEqualTo(p.version()+1);
        assertThat(saved.image()).isNull();
    }
    @Test void decoderRejectsOversizedDimensionsAndNonImagePayload() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4097,1,BufferedImage.TYPE_INT_RGB),"png",bytes);
        assertThatThrownBy(()->processor.png(bytes.toByteArray())).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->processor.png(new byte[3*1024*1024+1])).isInstanceOf(BaseException.class);
    }
}
