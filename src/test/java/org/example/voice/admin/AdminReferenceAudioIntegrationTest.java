package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import org.example.voice.admin.application.*;
import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.admin.domain.model.AdminData.ContentDraft;
import org.example.voice.admin.domain.port.*;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.infrastructure.AdminPersistence;
import org.example.voice.common.exception.*;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.domain.entity.ContentCategory;
import org.example.voice.practicecontent.domain.port.ReferenceAudioAssets;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc @Transactional @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminReferenceAudioIntegrationTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminContentService contents;
    @Autowired AdminReferenceAudioService service;
    @Autowired AdminReferenceAudioTransactions audioTransactions;
    @Autowired AdminAudioStore audioStore;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoSpyBean AdminPersistence store;
    @MockitoBean AdminAudioStorage storage;
    @MockitoBean AdminContentInvalidation invalidation;
    @MockitoBean ReferenceAudioAssets publicAssets;
    long actor,other,content;
    boolean assertOutsideTransaction;

    @BeforeAll void existingJdbcAudioSchema() {
        // These existing TTS columns/tables are migration-managed, not mapped by JPA's H2 DDL.
        jdbc.execute("alter table practice_contents add column if not exists tts_revision integer default 1 not null");
        jdbc.execute("alter table reference_audios add column if not exists tts_job_id bigint");
        jdbc.execute("create table if not exists example_tts_jobs(id bigint primary key,state varchar(20),object_key varchar(512),text_revision integer,profile_revision varchar(120),audio_sha256 varchar(64))");
        jdbc.execute("create table if not exists example_audio_approvals(job_id bigint,audio_sha256 varchar(64),decision varchar(20))");
    }

    @BeforeEach void fixture() {
        assertOutsideTransaction=false;
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var first=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","operator",OffsetDateTime.now());
            var second=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","other",OffsetDateTime.now());
            ReflectionTestUtils.setField(first,"role",UserRole.ADMIN);ReflectionTestUtils.setField(second,"role",UserRole.ADMIN);
            em.persist(first);em.persist(second);em.flush();actor=first.getId();other=second.getId();
            if(em.createQuery("select count(t) from ContentCategory t where t.contentType=:type and t.code='ANNOUNCER_PRACTICE'",Long.class)
                    .setParameter("type",ContentType.ANNOUNCER).getSingleResult()==0) {
                var tax=org.springframework.beans.BeanUtils.instantiateClass(ContentCategory.class);
                ReflectionTestUtils.setField(tax,"contentType",ContentType.ANNOUNCER);ReflectionTestUtils.setField(tax,"code","ANNOUNCER_PRACTICE");
                ReflectionTestUtils.setField(tax,"label","아나운서");ReflectionTestUtils.setField(tax,"sortOrder",1);em.persist(tax);
            }
            content=contents.create(actor,draft("오늘 아침 날씨를 전해 드립니다."),"등록").id();
        });
        when(storage.sign(any())).thenAnswer(call -> { var u=call.getArgument(0,Upload.class);
            return new SignedUpload("https://storage.example.invalid/upload",Map.of("Content-Type",u.contentType()),u.expiresAt()); });
        when(storage.verifyAndStore(any())).thenAnswer(call -> {
            if(assertOutsideTransaction)assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var u=call.getArgument(0,Upload.class);return new VerifiedAudio("reference-audios/announcer/"+u.contentId()+"/"+u.id()+u.extension(),2500);
        });
        when(storage.playback(any())).thenAnswer(call -> new Playback(call.getArgument(0,Audio.class).id(),"https://storage.example.invalid/preview",OffsetDateTime.now().plusMinutes(10)));
        when(publicAssets.playback(any())).thenReturn(new ReferenceAudioAssets.Playback("https://storage.example.invalid/public",OffsetDateTime.now().plusMinutes(10)));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void draftAudioCanBePreviewedByAdminAndReusesUserApisAfterPublication() throws Exception {
        UUID upload=issue();
        var first=service.complete(actor,content,upload,version(),"전문 아나운서",false,"음성 등록");
        assertThat(first.created()).isTrue();assertThat(first.audio().primary()).isTrue();assertThat(first.audio().durationMs()).isEqualTo(2500);
        mvc.perform(get("/api/admin/reference-audios/{id}/playback-url",first.audio().id()).with(auth(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.playbackUrl").value("https://storage.example.invalid/preview"));
        mvc.perform(get("/api/practice-contents/{id}/reference-audios",content).with(auth(other))).andExpect(status().isNotFound());
        contents.publication(actor,content,version(),PublishStatus.PUBLISHED,"공개");
        assertThat(em.createNativeQuery("select status from practice_contents where id=:id",String.class).setParameter("id",content).getSingleResult()).isEqualTo("PUBLISHED");
        mvc.perform(get("/api/practice-contents/{id}/reference-audios",content).with(auth(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(first.audio().id()))
                .andExpect(jsonPath("$.data.items[0].durationMs").value(2500));
        mvc.perform(get("/api/reference-audios/{id}/playback-url",first.audio().id()).with(auth(other))).andExpect(status().isOk());
    }
    @Test void repeatedCompletionDoesNotDuplicateAudioOrReapplyPrimary() {
        UUID first=issue();var a=service.complete(actor,content,first,version(),"화자 1",true,"등록");
        UUID second=issue();var b=service.complete(actor,content,second,version(),"화자 2",true,"등록");
        var replay=service.complete(actor,content,first,0,"화자 1",true,"등록");
        assertThat(replay.created()).isFalse();assertThat(replay.audio().id()).isEqualTo(a.audio().id());assertThat(replay.audio().primary()).isFalse();
        assertThat(audioTransactions.list(actor,content).items()).hasSize(2).filteredOn(Audio::primary).extracting(Audio::id).containsExactly(b.audio().id());
        verify(storage,times(2)).verifyAndStore(any());
        assertThatThrownBy(() -> service.complete(actor,content,first,version(),"다른 화자",true,"등록")).isInstanceOf(BaseException.class);
    }
    @Test void httpUploadAndRegistrationReturnServerMetadataAndSupportIdenticalRetry() throws Exception {
        String issued=mvc.perform(post("/api/admin/contents/{id}/reference-audios/upload-url",content).with(auth(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version",version(),"fileName","voice.m4a","contentType","audio/mp4","fileSizeBytes",100,"sha256","a".repeat(64),"reason","업로드"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.requiredHeaders").isMap()).andReturn().getResponse().getContentAsString();
        String upload=json.readTree(issued).path("data").path("uploadId").asText();
        String body=json.writeValueAsString(Map.of("uploadId",upload,"version",version(),"speakerName","아나운서","primary",true,"reason","등록"));
        mvc.perform(post("/api/admin/contents/{id}/reference-audios",content).with(auth(actor)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.durationMs").value(2500)).andExpect(jsonPath("$.data.objectKey").doesNotExist());
        mvc.perform(post("/api/admin/contents/{id}/reference-audios",content).with(auth(actor)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        verify(storage,times(1)).verifyAndStore(any());
    }
    @Test void primaryChangesAreVersionedAndNeverOverwriteText() throws Exception {
        var a=service.complete(actor,content,issue(),version(),"화자 1",true,"등록");
        var b=service.complete(actor,content,issue(),version(),"화자 2",false,"등록");
        long before=version();
        mvc.perform(patch("/api/admin/reference-audios/{id}/primary",b.audio().id()).with(auth(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version",before,"reason","대표 변경")))).andExpect(status().isOk());
        assertThat(version()).isGreaterThan(before);
        assertThat(store.content(content).orElseThrow().scriptText()).isEqualTo("오늘 아침 날씨를 전해 드립니다.");
        assertThat(audioTransactions.list(actor,content).items()).filteredOn(Audio::primary).extracting(Audio::id).containsExactly(b.audio().id());
        mvc.perform(patch("/api/admin/reference-audios/{id}/primary",a.audio().id()).with(auth(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version",before,"reason","stale")))).andExpect(status().isConflict());
    }
    @Test void ownerScopeExpiryAndScriptChangesAreCheckedBeforeStorageIo() {
        UUID upload=issue();clearInvocations(storage);
        assertThatThrownBy(() -> service.complete(other,content,upload,version(),"화자",true,"등록")).isInstanceOf(BaseException.class);
        verify(storage,never()).verifyAndStore(any());
        contents.edit(actor,content,version(),draft("변경한 다른 원문입니다."),"원문 변경");
        assertThatThrownBy(() -> service.complete(actor,content,upload,version(),"화자",true,"등록")).isInstanceOf(BaseException.class);
        verify(storage,never()).verifyAndStore(any());
    }
    @Test void expiredIntentCannotRegisterAndWrongExtensionCannotCreateAnIntent() throws Exception {
        UUID upload=issue();
        em.createQuery("update ReferenceAudioUpload u set u.expiresAt=:expired where u.id=:id")
                .setParameter("expired",OffsetDateTime.now().minusMinutes(1)).setParameter("id",upload).executeUpdate();em.clear();
        mvc.perform(post("/api/admin/contents/{id}/reference-audios",content).with(auth(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("uploadId",upload.toString(),"version",version(),"speakerName","화자","primary",true,"reason","등록"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_AUDIO_UPLOAD_EXPIRED"));
        verify(storage,never()).verifyAndStore(any());
        mvc.perform(post("/api/admin/contents/{id}/reference-audios/upload-url",content).with(auth(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version",version(),"fileName","../other.mp3","contentType","audio/mpeg","fileSizeBytes",3,"sha256","a".repeat(64),"reason","등록"))))
                .andExpect(status().isBadRequest());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void storageRunsOutsideTransactionsAndAuditFailureRollsBackTheLink() {
        UUID upload=issue();assertOutsideTransaction=true;
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("audit unavailable"))
                .when(store).audit(argThat(log -> "REFERENCE_AUDIO_REGISTER".equals(log.getAction())));
        assertThatThrownBy(() -> service.complete(actor,content,upload,version(),"화자",true,"등록")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            assertThat(audioStore.audios(content)).isEmpty();
            assertThat(audioStore.upload(upload,actor,content).orElseThrow().audioId()).isNull();
        });
    }
    @Test void userAndOtherContentTypesCannotUseTheUploadApi() throws Exception {
        mvc.perform(get("/api/admin/contents/{id}/reference-audios",content)).andExpect(status().isUnauthorized());
        em.createQuery("update User u set u.role=:role where u.id=:id").setParameter("role",UserRole.USER).setParameter("id",other).executeUpdate();
        mvc.perform(get("/api/admin/contents/{id}/reference-audios",content).with(auth(other))).andExpect(status().isForbidden());
        var sentence=org.example.voice.practicecontent.domain.entity.PracticeContent.draft(ContentType.SENTENCE,LearningFocus.PRONUNCIATION,
                "FINAL_CONSONANT",Difficulty.BEGINNER,"문장",null,"일반 문장입니다.",List.of(),5,OffsetDateTime.now());
        em.persist(sentence);em.flush();
        assertThatThrownBy(() -> service.issue(actor,sentence.getId(),new UploadCommand(0,"voice.m4a","audio/mp4",100,"a".repeat(64),"등록")))
                .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private UUID issue() { return service.issue(actor,content,new UploadCommand(version(),"voice.m4a","audio/mp4",100,"a".repeat(64),"음성 업로드")).uploadId(); }
    private long version() { return new TransactionTemplate(manager).execute(tx -> store.content(content).orElseThrow().version()); }
    private ContentDraft draft(String text) { return new ContentDraft(ContentType.ANNOUNCER,LearningFocus.PRONUNCIATION,"ANNOUNCER_PRACTICE",Difficulty.BEGINNER,"아나운서 연습",null,text,List.of(),8); }
    private RequestPostProcessor auth(long id) { return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))); }
}
