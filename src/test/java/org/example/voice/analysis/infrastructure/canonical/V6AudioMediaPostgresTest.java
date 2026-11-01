package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.training.domain.entity.*;
import org.example.voice.training.domain.type.*;
import org.example.voice.training.domain.model.MediaPreparationData;
import org.example.voice.training.domain.port.RecordingMediaPreparationStore;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class V6AudioMediaPostgresTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired CanonicalExecutionMediaStore media;
    @Autowired RecordingMediaPreparationStore preparations;
    @Autowired RunPodContract contract;
    @Autowired org.example.voice.title.infrastructure.TitlePersistence titles;
    @org.springframework.test.context.bean.override.mockito.MockitoBean CanonicalCommittedResultReader committed;
    final ObjectMapper mapper=new ObjectMapper();
    final UUID execution=UUID.randomUUID();
    VoiceRecording recording;
    AnalysisResult analysis;

    @BeforeEach void setup() throws Exception {
        var now=OffsetDateTime.now();
        var user=User.createLocal(UUID.randomUUID()+"@example.invalid","fixture-hash","v6 fixture",now);em.persist(user);
        var constructor=PracticeContent.class.getDeclaredConstructor();constructor.setAccessible(true);
        var content=constructor.newInstance();
        java.util.Map<String,Object> fields=java.util.Map.of("contentType",ContentType.SENTENCE,"learningFocus",LearningFocus.PRONUNCIATION,
                "title","fixture","scriptText","fixture script","difficulty",Difficulty.BEGINNER,"status",PublishStatus.PUBLISHED,"createdAt",now,"updatedAt",now);
        fields.forEach((k,v)->ReflectionTestUtils.setField(content,k,v));em.persist(content);
        var session=TrainingSession.create(user.getId(),content,null,LearningFocus.PRONUNCIATION);em.persist(session);
        recording=VoiceRecording.create(session,1,"recordings/fixture.wav","audio/wav",100L,1000,"b".repeat(64),RecordingQualityStatus.PASS,BigDecimal.ONE,BigDecimal.ONE);
        recording.select();em.persist(recording);
        var result=AnalysisResult.pending(recording,UUID.randomUUID());analysis=result;em.persist(result);em.flush();
        jdbc.update("""
          INSERT INTO analysis_canonical_executions(execution_id,request_id,analysis_id,recording_id,content_id,
            analysis_profile,request_schema_version,result_schema_version,request_payload_sha256,script_sha256,audio_sha256,deadline_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
          """,execution,UUID.randomUUID(),result.getId(),recording.getId(),content.getId(),RunPodContract.AUDIOVISUAL_PROFILE,
                RunPodContract.REQUEST_V4,RunPodContract.RESULT_V6,"a".repeat(64),"a".repeat(64),recording.getAudioSha256(),now.plusMinutes(5));
    }
    ObjectNode request() {
        var n=mapper.createObjectNode().put("recordingId",recording.getId());
        n.putObject("audio").put("sha256",recording.getAudioSha256());n.putNull("video");n.putNull("mediaPreparation");return n;
    }
    @Test void receiptlessAudioIsBoundToTheActualExecutionAndRecording() {
        media.snapshot(execution,recording.getId(),recording.getAudioSha256(),null);
        assertThatCode(()->media.requireRequest(execution,request())).doesNotThrowAnyException();
        assertThatThrownBy(()->media.requireRequest(UUID.randomUUID(),request())).isInstanceOf(RunPodContractException.class);
        var forged=request();((ObjectNode)forged.get("audio")).put("sha256","f".repeat(64));
        assertThatThrownBy(()->media.requireRequest(execution,forged)).isInstanceOf(RunPodContractException.class);
    }
    @Test void preparedAudioMustRetainItsRealReceiptAndMayNotDropIt() {
        var receipt=new MediaPreparationData(MediaPreparationData.SCHEMA,"c".repeat(64),recording.getAudioSha256(),null,
                MediaPreparationData.REVISION,BigDecimal.ZERO,null,"1/16000",null,"NOT_APPLICABLE");
        preparations.register(recording.getId(),receipt);
        media.snapshot(execution,recording.getId(),recording.getAudioSha256(),null);
        var n=request();var prep=n.putObject("mediaPreparation");prep.set("receipt",mapper.valueToTree(receipt));
        prep.put("receiptSha256",contract.digest(prep.get("receipt")));
        assertThatCode(()->media.requireRequest(execution,n)).doesNotThrowAnyException();
        assertThatThrownBy(()->media.requireRequest(execution,request())).isInstanceOf(RunPodContractException.class);
        prep.put("receiptSha256","f".repeat(64));
        assertThatThrownBy(()->media.requireRequest(execution,n)).isInstanceOf(RunPodContractException.class);
    }
    @Test void audioResultBindsToCurrentMediaWithoutFabricatingVideo() throws Exception {
        var projection=V6AudioFixtures.failure().put("executionId",execution.toString()).put("recordingId",recording.getId());
        var doc=CanonicalCallbackDocument.parse(projection.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),contract);
        assertThatCode(()->media.requireResult(doc)).doesNotThrowAnyException();
        ((ObjectNode)projection.get("mediaBinding")).put("mediaReceiptSha256","e".repeat(64));
        var wrong=CanonicalCallbackDocument.parse(projection.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),contract);
        assertThatThrownBy(()->media.requireResult(wrong)).isInstanceOf(RunPodContractException.class);
    }
    @Test void v6TitleScoreUsesCommittedProofAndNeverTheMutableScoreColumn() {
        analysis.assignExecution(UUID.randomUUID(),OffsetDateTime.now().plusMinutes(5));
        analysis.assignAudiovisualProfile();
        ReflectionTestUtils.setField(analysis,"status",org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED);
        ReflectionTestUtils.setField(analysis,"overallScore",new BigDecimal("99"));
        var session=recording.getTrainingSession();session.startAnalysis();em.flush();
        var document=mock(CanonicalCallbackDocument.class);
        when(committed.findCurrent(any())).thenReturn(java.util.Optional.of(document));
        when(document.hasRequiredStorageEvidence()).thenReturn(true);
        when(document.decision()).thenReturn(new CanonicalCallbackDocument.Decision(CanonicalCallbackDocument.DecisionStatus.ACCEPT,null,null));
        when(document.representation()).thenReturn("INLINE");when(document.feedbackDeliveryAllowed()).thenReturn(true);
        when(document.adapterStatus()).thenReturn("READY");when(document.generationStatus()).thenReturn("DETERMINISTIC_FALLBACK");
        when(document.overallScore()).thenReturn(new BigDecimal("83.50"));
        assertThat(titles.score(analysis.getId(),session.getUserId(),session.getId(),session.getContent().getId()))
                .contains(new BigDecimal("83.50"));
        when(committed.findCurrent(any())).thenReturn(java.util.Optional.empty());
        assertThat(titles.score(analysis.getId(),session.getUserId(),session.getId(),session.getContent().getId())).isEmpty();
        assertThat(titles.coachingScoreUnavailable(analysis.getId(),session.getUserId(),session.getId(),session.getContent().getId())).isTrue();
    }
}
