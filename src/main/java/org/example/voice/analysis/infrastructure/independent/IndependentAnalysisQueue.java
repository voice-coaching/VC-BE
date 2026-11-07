package org.example.voice.analysis.infrastructure.independent;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;
import org.example.voice.analysis.domain.port.IndependentAnalysisClient.Job;
import org.example.voice.analysis.domain.port.IndependentAnalysisClient.Result;
import org.example.voice.analysis.domain.port.IndependentAnalysisSubmission;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.practicecontent.domain.port.PrivateTextCipher;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Database state machine. Every transaction is short; HTTP and media reads are outside. */
@Repository @RequiredArgsConstructor
public class IndependentAnalysisQueue implements IndependentAnalysisSubmission {
    private final JdbcTemplate jdbc;
    private final AnalysisResultJpaRepository analyses;
    private final IndependentAnalysisProperties properties;
    private final CoachingUsageService usage;
    private final PrivateTextCipher cipher;
    private static final String CURRENT="""
        EXISTS(SELECT 1 FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id
          JOIN training_sessions s ON s.id=r.training_session_id JOIN users u ON u.id=s.user_id
          WHERE a.id=q.analysis_id AND a.active_request_event_id=q.client_request_id::text
          AND a.active_execution_id=q.backend_execution_id::text AND a.analysis_profile='INDEPENDENT_PULL_V1'
          AND a.status IN ('PENDING','PROCESSING') AND r.id=q.recording_id AND s.user_id=q.owner_id
          AND r.deleted_at IS NULL AND r.is_selected=TRUE AND s.status<>'CANCELED'
          AND u.status='ACTIVE' AND u.deleted_at IS NULL)
        """;

    @Transactional(propagation=Propagation.MANDATORY)
    public void lockOwner(Long ownerId) {
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE' AND deleted_at IS NULL FOR UPDATE",ownerId).isEmpty())
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void assertSession(Long sessionId,Long ownerId) {
        // Validate before startAnalysis can overwrite a cancelled session's state.
        if(jdbc.queryForList("SELECT id FROM training_sessions WHERE id=? AND user_id=? AND status<>'CANCELED' FOR UPDATE",sessionId,ownerId).isEmpty())
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(AnalysisWorkerRequest request,Long ownerId) {
        if(!properties.acceptsSubmissions()) throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        var analysis=analyses.findForIngestion(request.analysisId()).orElseThrow(()->new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));
        var recording=analysis.getRecording();var session=recording.getTrainingSession();
        if(!Objects.equals(session.getUserId(),ownerId) || !Boolean.TRUE.equals(recording.getSelected())
                || session.getStatus()==org.example.voice.training.domain.type.TrainingSessionStatus.CANCELED
                || recording.getDeletedAt()!=null || !request.eventId().toString().equals(analysis.getActiveRequestEventId())
                || analysis.getStatus()!=AnalysisStatus.PENDING) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        if(request.visualInput()!=null || !"audio/wav".equalsIgnoreCase(request.mimeType())
                || request.fileSizeBytes()==null || request.fileSizeBytes()<=0 || request.fileSizeBytes()>20971520
                || request.audioSha256()==null || !request.audioSha256().matches("[a-f0-9]{64}")
                || request.scriptText()==null || request.scriptText().isBlank()
                || !IndependentResultDocument.sha256(request.scriptText().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(request.scriptSha256()))
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        if(request.scriptText().codePointCount(0,request.scriptText().length())>300)
            throw new BaseException(ErrorCode.COACHING_SCRIPT_LIMIT_EXCEEDED);
        if(request.durationMs()==null || request.durationMs()<500 || request.durationMs()>90000)
            throw new BaseException(ErrorCode.COACHING_AUDIO_LIMIT_EXCEEDED);
        var allowance=usage.getUsage(ownerId);
        if(allowance.expiresAt()!=null && !java.time.OffsetDateTime.now().isBefore(allowance.expiresAt()))
            throw new BaseException(ErrorCode.COACHING_PERIOD_EXPIRED);
        if(!allowance.canRequestCoaching()) throw new BaseException(allowance.remaining()!=null && allowance.remaining()==0
                ?ErrorCode.COACHING_QUOTA_EXCEEDED:ErrorCode.COACHING_PERIOD_EXPIRED);
        usage.reserve(ownerId,analysis.getId(),recording.getId(),request.eventId(),request.scriptText(),request.durationMs());
        var execution=UUID.randomUUID();analysis.assignIndependentExecution(execution);analyses.saveAndFlush(analysis);
        jdbc.update("""
            INSERT INTO independent_analysis_attempts(client_request_id,backend_execution_id,analysis_id,recording_id,
                owner_id,session_id,content_id,audio_object_key,audio_sha256,audio_size,script_ciphertext,script_sha256)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,request.eventId(),execution,analysis.getId(),recording.getId(),ownerId,session.getId(),request.contentId(),
                request.audioObjectKey(),request.audioSha256(),request.fileSizeBytes(),cipher.encrypt(request.scriptText()),request.scriptSha256());
    }

    @Transactional(timeout=5)
    public Optional<IndependentAnalysisAttempt> claim(String phase) {
        String stages=switch(phase) {
            case "SUBMIT" -> "'SUBMIT_PENDING','LOOKUP_PENDING'";
            case "COLLECT" -> "'COLLECTING','COLLECTING_FAILED'";
            case "ACK" -> "'ACK_PENDING'";
            default -> throw new IllegalArgumentException("Unknown independent phase");
        };
        var rows=jdbc.queryForList("SELECT q.*,"+CURRENT+" AS current_attempt FROM independent_analysis_attempts q WHERE q.stage IN ("+stages+")"
                +" AND q.next_attempt_at<=CURRENT_TIMESTAMP AND (q.claim_until IS NULL OR q.claim_until<CURRENT_TIMESTAMP)"
                +" ORDER BY q.next_attempt_at,q.created_at LIMIT 1 FOR UPDATE OF q SKIP LOCKED");
        if(rows.isEmpty())return Optional.empty();
        var row=rows.getFirst();var claim=UUID.randomUUID();int attempts=Math.min(Integer.MAX_VALUE-1,((Number)row.get("attempt_count")).intValue())+1;
        jdbc.update("UPDATE independent_analysis_attempts SET claim_id=?,claim_until=CURRENT_TIMESTAMP+INTERVAL '120 seconds',attempt_count=?,updated_at=CURRENT_TIMESTAMP WHERE client_request_id=?",claim,attempts,row.get("client_request_id"));
        row.put("claim_id",claim);row.put("attempt_count",attempts);
        return Optional.of(snapshot(row));
    }
    private IndependentAnalysisAttempt snapshot(Map<String,Object> r) {
        return new IndependentAnalysisAttempt((UUID)r.get("client_request_id"),(UUID)r.get("backend_execution_id"),
                number(r,"analysis_id"),number(r,"recording_id"),number(r,"owner_id"),number(r,"session_id"),number(r,"content_id"),
                (String)r.get("audio_object_key"),(String)r.get("audio_sha256"),number(r,"audio_size"),(String)r.get("script_ciphertext"),
                (String)r.get("script_sha256"),org.example.voice.analysis.domain.type.IndependentAnalysisStage.valueOf((String)r.get("stage")),(UUID)r.get("ai_job_id"),(UUID)r.get("ai_execution_id"),
                (String)r.get("ai_status"),(UUID)r.get("claim_id"),((Number)r.get("attempt_count")).intValue(),
                (String)r.get("result_sha256"),(byte[])r.get("result_bytes"),Boolean.TRUE.equals(r.get("current_attempt")),r.get("cancel_requested_at")!=null);
    }
    private static long number(Map<String,Object> r,String key) { return ((Number)r.get(key)).longValue(); }
    @Transactional(readOnly=true,timeout=5)
    public Optional<IndependentAnalysisAttempt> current(long analysisId,long ownerId,String request,String execution) {
        return jdbc.queryForList("SELECT q.*,FALSE AS current_attempt FROM independent_analysis_attempts q WHERE q.analysis_id=? AND q.owner_id=? AND q.client_request_id::text=? AND q.backend_execution_id::text=?",
                analysisId,ownerId,request,execution).stream().findFirst().map(this::snapshot);
    }
    public String script(IndependentAnalysisAttempt attempt) {
        String text=cipher.decrypt(attempt.scriptCiphertext());
        if (!IndependentResultDocument.sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(attempt.scriptSha256()))
            throw new IndependentTransportException(422,"SCRIPT_DIGEST_MISMATCH",0);
        return text;
    }
    private Optional<Map<String,Object>> owned(IndependentAnalysisAttempt a) {
        return jdbc.queryForList("SELECT * FROM independent_analysis_attempts WHERE client_request_id=? AND claim_id=? AND claim_until>CURRENT_TIMESTAMP FOR UPDATE",
                a.clientRequestId(),a.claimId()).stream().findFirst();
    }
    private boolean current(AnalysisResult result,IndependentAnalysisAttempt a) {
        return result!=null && result.isIndependentExecution() && a.clientRequestId().toString().equals(result.getActiveRequestEventId())
                && a.backendExecutionId().toString().equals(result.getActiveExecutionId())
                && (result.getStatus()==AnalysisStatus.PENDING || result.getStatus()==AnalysisStatus.PROCESSING)
                && Boolean.TRUE.equals(jdbc.queryForObject("SELECT "+CURRENT+" FROM independent_analysis_attempts q WHERE q.client_request_id=?",Boolean.class,a.clientRequestId()));
    }
    @Transactional(timeout=5)
    public boolean beforeSubmit(IndependentAnalysisAttempt a) {
        return jdbc.update("UPDATE independent_analysis_attempts q SET stage='LOOKUP_PENDING' WHERE client_request_id=? AND claim_id=? AND claim_until>CURRENT_TIMESTAMP AND "+CURRENT,a.clientRequestId(),a.claimId())==1;
    }
    public static void correlate(IndependentAnalysisAttempt a,Job job) {
        if(!a.clientRequestId().equals(job.clientRequestId()) || (a.jobId()!=null && !a.jobId().equals(job.jobId()))
                || (a.aiExecutionId()!=null && !a.aiExecutionId().equals(job.executionId())))
            throw new IndependentTransportException(0,"AI_CORRELATION_MISMATCH",0);
    }
    @Transactional(timeout=5)
    public void accepted(IndependentAnalysisAttempt a,Job job) {
        correlate(a,job);var analysis=analyses.findForIngestion(a.analysisId()).orElse(null);
        if(owned(a).isEmpty())return;
        jdbc.update("""
            UPDATE independent_analysis_attempts SET ai_job_id=?,ai_execution_id=?,ai_status=?,ai_sequence=?,stage='COLLECTING',
                next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '3 seconds',attempt_count=0,last_error_code=NULL,
                claim_id=NULL,claim_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE client_request_id=?
            """,job.jobId(),job.executionId(),job.status(),job.sequence(),a.clientRequestId());
        if(current(analysis,a)) { analysis.markProcessing();analyses.save(analysis); }
    }
    @Transactional(timeout=5)
    public void waiting(IndependentAnalysisAttempt a,Job job) {
        correlate(a,job);var row=owned(a);if(row.isEmpty())return;
        if(job.sequence()<number(row.get(),"ai_sequence"))throw new IndependentTransportException(0,"AI_SEQUENCE_REGRESSION",0);
        jdbc.update("""
            UPDATE independent_analysis_attempts SET ai_status=?,ai_sequence=?,stage='COLLECTING',attempt_count=0,
                next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),claim_id=NULL,claim_until=NULL,
                last_error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE client_request_id=?
            """,job.status(),job.sequence(),4+java.util.concurrent.ThreadLocalRandom.current().nextInt(4),a.clientRequestId());
    }
    @Transactional(timeout=5)
    public void remember(IndependentAnalysisAttempt a,Job job,Result raw) {
        correlate(a,job);var row=owned(a);if(row.isEmpty())return;
        String old=(String)row.get().get("result_sha256");
        if(job.sequence()<number(row.get(),"ai_sequence")) throw new IndependentTransportException(0,"AI_SEQUENCE_REGRESSION",0);
        if(old!=null && !old.equals(raw.sha256())) throw new IndependentTransportException(0,"AI_RESULT_CHANGED",0);
        jdbc.update("UPDATE independent_analysis_attempts SET ai_status=?,ai_sequence=?,result_sha256=?,result_bytes=?,collected_at=COALESCE(collected_at,CURRENT_TIMESTAMP) WHERE client_request_id=?",
                job.status(),job.sequence(),raw.sha256(),raw.bytes(),a.clientRequestId());
    }
    @Transactional(timeout=5)
    public void complete(IndependentAnalysisAttempt a,Job job,Result raw,IndependentResultDocument document) {
        correlate(a,job);var analysis=analyses.findForIngestion(a.analysisId()).orElse(null);var row=owned(a);
        if(row.isEmpty())return;
        if(!raw.sha256().equals(row.get().get("result_sha256"))) throw new IndependentTransportException(0,"AI_RESULT_CHANGED",0);
        boolean delivered=false;
        if(current(analysis,a)) {
            var status="COMPLETED".equals(job.status())?AnalysisStatus.COMPLETED:AnalysisStatus.FAILED;
            delivered=status==AnalysisStatus.COMPLETED && document.accepted() && (document.feedback()!=null || document.score()!=null);
            analysis.finishIndependent(a.clientRequestId(),status,document.score(),document.feedback(),safeCode(document.failure()),delivered);
            analyses.saveAndFlush(analysis);
            settle(a,delivered);
        } else settle(a,false);
        jdbc.update("""
            UPDATE independent_analysis_attempts SET stage='ACK_PENDING',committed_at=CURRENT_TIMESTAMP,
                next_attempt_at=CURRENT_TIMESTAMP,attempt_count=0,claim_id=NULL,claim_until=NULL,last_error_code=NULL
                WHERE client_request_id=?
            """,a.clientRequestId());
    }
    @Transactional(timeout=5)
    public void terminalWithoutResult(IndependentAnalysisAttempt a,Job job) {
        correlate(a,job);var analysis=analyses.findForIngestion(a.analysisId()).orElse(null);if(owned(a).isEmpty())return;
        if(current(analysis,a)) {
            analysis.finishIndependent(a.clientRequestId(),AnalysisStatus.FAILED,null,null,safeCode(job.failureCode()),false);
            analyses.saveAndFlush(analysis);settle(a,false);
        } else settle(a,false);
        jdbc.update("UPDATE independent_analysis_attempts SET stage='ACK_PENDING',ai_status=?,ai_sequence=?,committed_at=CURRENT_TIMESTAMP,next_attempt_at=CURRENT_TIMESTAMP,claim_id=NULL,claim_until=NULL WHERE client_request_id=?",
                job.status(),job.sequence(),a.clientRequestId());
    }
    @Transactional(timeout=5)
    public void effectsWithoutResult(IndependentAnalysisAttempt a) {
        jdbc.update("UPDATE independent_analysis_attempts SET stage='FINISHED',claim_id=NULL,claim_until=NULL,last_error_code=NULL WHERE client_request_id=? AND claim_id=? AND claim_until>CURRENT_TIMESTAMP AND result_sha256 IS NULL AND committed_at IS NOT NULL",a.clientRequestId(),a.claimId());
    }
    @Transactional(timeout=5)
    public void canceledBeforeSubmit(IndependentAnalysisAttempt a) {
        if(owned(a).isPresent()) {
            jdbc.update("UPDATE independent_analysis_attempts SET stage='CANCELLED',claim_id=NULL,claim_until=NULL WHERE client_request_id=?",a.clientRequestId());
            settle(a,false);
        }
    }
    @Transactional(timeout=5)
    public void cancelRequested(IndependentAnalysisAttempt a) {
        jdbc.update("UPDATE independent_analysis_attempts SET cancel_requested_at=CURRENT_TIMESTAMP WHERE client_request_id=? AND claim_id=? AND claim_until>CURRENT_TIMESTAMP",a.clientRequestId(),a.claimId());
    }
    @Transactional(timeout=5)
    public void acknowledged(IndependentAnalysisAttempt a,Job response) {
        correlate(a,response);
        if(response.acknowledgedAt()==null || !Objects.equals(a.resultSha256(),response.resultSha256()))
            throw new IndependentTransportException(0,"AI_ACK_UNCONFIRMED",0);
        jdbc.update("""
            UPDATE independent_analysis_attempts SET stage='ACKED',acknowledged_at=?,claim_id=NULL,claim_until=NULL,
                last_error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE client_request_id=? AND claim_id=?
                AND claim_until>CURRENT_TIMESTAMP AND committed_at IS NOT NULL
            """,response.acknowledgedAt(),a.clientRequestId(),a.claimId());
    }
    @Transactional(timeout=5)
    public void retry(IndependentAnalysisAttempt a,String phase,IndependentTransportException error) {
        var analysis="SUBMIT".equals(phase)?analyses.findForIngestion(a.analysisId()).orElse(null):null;
        if(owned(a).isEmpty())return;
        boolean rejected="SUBMIT".equals(phase) && error.inputRejected();
        String stage=rejected?"SUBMISSION_REJECTED":"SUBMIT".equals(phase)?"LOOKUP_PENDING":"ACK".equals(phase)?"ACK_PENDING":"COLLECTING_FAILED";
        long seconds=Math.max(error.retryAfterSeconds(),Math.min(300L,1L<<Math.min(a.attempts(),8)));
        if(error.status()==401 || error.status()==403) seconds=Math.max(seconds,300);
        seconds+=java.util.concurrent.ThreadLocalRandom.current().nextInt(4);
        jdbc.update("""
            UPDATE independent_analysis_attempts SET stage=?,next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),
                last_http_status=?,last_error_code=?,claim_id=NULL,claim_until=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE client_request_id=?
            """,stage,seconds,error.status()==0?null:Integer.valueOf(error.status()),error.getMessage(),a.clientRequestId());
        if(rejected && current(analysis,a)) {
            analysis.finishIndependent(a.clientRequestId(),AnalysisStatus.FAILED,null,null,"INDEPENDENT_SUBMISSION_REJECTED",false);
            analyses.saveAndFlush(analysis);settle(a,false);
        } else if(rejected) settle(a,false);
    }
    private void settle(IndependentAnalysisAttempt a,boolean delivered) {
        // V61 removed the legacy settlement trigger; independent attempts settle explicitly once.
        jdbc.update("UPDATE coaching_usage_reservations SET state=? WHERE analysis_id=? AND request_event_id=? AND state='RESERVED'",
                delivered?"CONSUMED":"RELEASED",a.analysisId(),a.clientRequestId());
    }
    private static String safeCode(String code) { return code!=null && code.matches("[A-Za-z0-9_.:-]{1,100}")?code:"AI_ANALYSIS_FAILED"; }
}
