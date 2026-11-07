package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.example.voice.training.domain.model.MediaPreparationData;
import org.example.voice.training.infrastructure.RecordingMediaPreparationStoreImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class LipMediaPostgresTest {
    @Test void mediaIdentityAndStoredProjectionSurviveTheRealPostgresBoundary() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres");
        String schema="lip_media_"+UUID.randomUUID().toString().replace("-","");
        var source=new DriverManagerDataSource(url,user,"");var admin=new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).schemas(schema).locations("classpath:db/migration").load().migrate();
            var db=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,""));
            db.update("insert into users(id,nickname,status,role,terms_agreed_at,privacy_agreed_at,created_at,updated_at) values(800001,'fixture','ACTIVE','USER',now(),now(),now(),now())");
            db.update("insert into training_sessions(id,user_id,content_id,learning_focus,status,started_at) values(800001,800001,3,'PRONUNCIATION','ANALYZING',now())");
            db.update("""
                insert into voice_recordings(id,training_session_id,attempt_no,audio_url,mime_type,file_size_bytes,duration_ms,quality_status,is_selected,created_at,audio_sha256,visual_sha256,visual_object_key)
                values(2,800001,1,'recordings/fixture.wav','audio/wav',100,1000,'PASS',true,now(),?,?,?)
                ""","b".repeat(64),"c".repeat(64),"recordings/fixture.mp4");
            var raw=LipProvisionalContractTest.result();var contract=new RunPodContract();
            var execution=UUID.fromString(raw.path("executionId").asText());
            var request=UUID.fromString(raw.path("requestId").asText());
            db.update("""
                insert into analysis_canonical_executions(execution_id,request_id,analysis_id,recording_id,content_id,analysis_profile,request_schema_version,result_schema_version,request_payload_sha256,script_sha256,audio_sha256,deadline_at)
                values(?,?,1,2,3,?,?,?, ?,?,?,now()+interval '5 minutes')
                """,execution,request,RunPodContract.AUDIOVISUAL_PROFILE,RunPodContract.REQUEST_V4,RunPodContract.RESULT_V6,"a".repeat(64),"a".repeat(64),"b".repeat(64));
            var preparation=new MediaPreparationData(MediaPreparationData.SCHEMA,"d".repeat(64),"b".repeat(64),"c".repeat(64),
                    MediaPreparationData.REVISION,BigDecimal.ZERO,BigDecimal.ZERO,"1/16000","1/12800","UNVERIFIED");
            var preparations=new RecordingMediaPreparationStoreImpl(db);preparations.register(2,preparation);
            var media=new CanonicalExecutionMediaStore(db,preparations,contract);
            media.snapshot(execution,2,"b".repeat(64),"c".repeat(64));
            var payload=LipProvisionalContractTest.MAPPER.createObjectNode().put("recordingId",2);
            payload.putObject("audio").put("sha256","b".repeat(64));payload.putObject("video").put("sha256","c".repeat(64));
            var input=payload.putObject("mediaPreparation");input.set("receipt",LipProvisionalContractTest.MAPPER.valueToTree(preparation));
            String sha=contract.digest(input.path("receipt"));input.put("receiptSha256",sha);
            media.requireRequest(execution,payload);
            ((com.fasterxml.jackson.databind.node.ObjectNode)raw.get("mediaBinding")).put("mediaReceiptSha256",sha);
            var doc=CanonicalCallbackDocument.parse(LipProvisionalContractTest.bytes(raw),contract);media.requireResult(doc);
            db.update("""
                insert into analysis_canonical_results(event_id,execution_id,request_id,analysis_id,recording_id,content_id,worker_instance_id,
                schema_version,analysis_profile,status,payload_sha256,raw_sha256,callback_bytes,callback_document)
                values(?,?,?,1,2,3,?,?,?,?,?,?,?,cast(? as json))
                """,doc.identity().eventId(),execution,request,doc.identity().workerId(),doc.schemaVersion(),doc.analysisProfile(),doc.status().name(),
                    doc.payloadSha256(),doc.rawSha256(),doc.bytes(),doc.storageJson());
            var analysis=mock(AnalysisResult.class);when(analysis.getId()).thenReturn(1L);
            when(analysis.getActiveExecutionId()).thenReturn(execution.toString());when(analysis.getActiveRequestEventId()).thenReturn(request.toString());
            when(analysis.getWorkerInstanceId()).thenReturn(doc.identity().workerId().toString());
            when(analysis.getStatus()).thenReturn(org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED);
            when(analysis.getCanonicalResultEventId()).thenReturn(doc.identity().eventId());
            when(analysis.getAnalysisProfile()).thenReturn(RunPodContract.AUDIOVISUAL_PROFILE);
            var recording=mock(org.example.voice.training.domain.entity.VoiceRecording.class);
            var session=mock(org.example.voice.training.domain.entity.TrainingSession.class);
            var content=mock(org.example.voice.practicecontent.domain.entity.PracticeContent.class);
            when(analysis.getRecording()).thenReturn(recording);when(recording.getId()).thenReturn(2L);
            when(recording.getTrainingSession()).thenReturn(session);when(session.getContent()).thenReturn(content);
            when(session.getUserId()).thenReturn(800001L);when(content.getId()).thenReturn(3L);
            var read=new CanonicalCommittedResultReader(db,contract).findCurrent(analysis).orElseThrow();
            var view=new CanonicalPublicProjection(contract).project(read);
            var json=LipProvisionalContractTest.MAPPER.valueToTree(view);
            assertThat(json.at("/visual/phoneAssessments/0/subScores/1/score").isNull()).isTrue();
            assertThat(json.at("/visual/phoneAssessments/0/subScores/0/score").decimalValue()).isEqualByComparingTo("0");
            assertThat(json.at("/score/overallScore").decimalValue()).isEqualByComparingTo("73.6");
            db.update("""
                insert into analysis_results(id,recording_id,status,created_at,analysis_profile,active_request_event_id,active_execution_id,worker_instance_id,canonical_result_event_id)
                values(1,2,'COMPLETED',now(),?,?,?,?,?)
                """,RunPodContract.AUDIOVISUAL_PROFILE,request.toString(),execution.toString(),doc.identity().workerId().toString(),doc.identity().eventId());
            var results=mock(org.example.voice.training.infrastructure.AnalysisResultJpaRepository.class);
            when(results.findById(1L)).thenReturn(java.util.Optional.of(analysis));
            var queries=new org.example.voice.analysis.application.CanonicalAnalysisQueryService(results,
                    mock(org.example.voice.analysis.domain.port.CanonicalExecutionRegistry.class),new CanonicalCommittedResultReader(db,contract),
                    new CanonicalViewVisibility(db),new CanonicalPublicProjection(contract),mock(CanonicalActionPolicy.class),
                    mock(CanonicalDeliverySpool.class),mock(CanonicalPublishedResults.class));
            var publicView=queries.get(1L,800001L);
            var wrapped=LipProvisionalContractTest.MAPPER.valueToTree(org.example.voice.common.response.ApiResponse.success("ok",publicView));
            assertThat(wrapped.at("/data/canonicalAnalysis/visual/phoneAssessments/0/scoreKind").asText()).isEqualTo("PROVISIONAL");
            assertThat(wrapped.at("/data/canonicalAnalysis/visual/score").isNull()).isTrue();
            input.put("receiptSha256","f".repeat(64));
            assertThatThrownBy(()->media.requireRequest(execution,payload)).isInstanceOf(RunPodContractException.class);
            assertThatThrownBy(()->media.snapshot(execution,2,"b".repeat(64),"f".repeat(64))).isInstanceOf(IllegalStateException.class);
        } finally { admin.execute("drop schema if exists "+schema+" cascade"); }
    }
}
