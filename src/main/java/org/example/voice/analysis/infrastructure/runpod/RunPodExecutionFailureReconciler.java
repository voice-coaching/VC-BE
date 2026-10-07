package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.canonical.CanonicalDeliverySpool;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;

/** Observe a confirmed worker failure without waiting for a lease timeout. Never re-submit inference. */
@Component
@ConditionalOnProperty(prefix="analysis", name="transport", havingValue="runpod_http")
public class RunPodExecutionFailureReconciler {
    private final JdbcTemplate jdbc;
    private final RunPodAnalysisClient client;
    private final AnalysisResultJpaRepository results;
    private final CanonicalDeliverySpool delivery;
    private final TransactionTemplate transactions;
    private long cursor;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService lane=Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"runpod-failure-reconciliation");thread.setDaemon(true);return thread;
    });

    public RunPodExecutionFailureReconciler(JdbcTemplate jdbc, RunPodAnalysisClient client,
            AnalysisResultJpaRepository results, CanonicalDeliverySpool delivery, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.client=client; this.results=results; this.delivery=delivery;
        this.transactions=new TransactionTemplate(manager); this.transactions.setTimeout(5);
    }
    private record Attempt(long analysisId, UUID requestId, UUID executionId, UUID workerId) {}

    @Scheduled(fixedDelayString="${analysis.runpod.failure-reconcile-interval:PT15S}")
    public void reconcile() {
        if(!running.compareAndSet(false,true))return;
        lane.execute(()->{try{reconcileBatch();}
            catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("runpod_failure_reconciliation_deferred type={}",error.getClass().getSimpleName());}
            finally{running.set(false);}});
    }

    private void reconcileBatch() {
        var attempts=jdbc.query("""
                SELECT id,active_request_event_id,active_execution_id,worker_instance_id FROM analysis_results
                WHERE id>? AND analysis_profile IN ('CANONICAL_HANDOFF_20261004_V5','CANONICAL_AUDIOVISUAL_20261007_V6')
                  AND status IN ('PENDING','PROCESSING') AND worker_instance_id IS NOT NULL
                  AND active_request_event_id IS NOT NULL AND active_execution_id IS NOT NULL
                  AND handoff_received_at IS NULL ORDER BY id LIMIT 8
                """,(row,i)->new Attempt(row.getLong(1),UUID.fromString(row.getString(2)),
                    UUID.fromString(row.getString(3)),UUID.fromString(row.getString(4))),cursor);
        if(attempts.isEmpty()){cursor=0;return;}
        for(var attempt:attempts){
            cursor=attempt.analysisId();
            var observed=client.status(attempt.requestId(),attempt.executionId()); // No transaction/lock during HTTP.
            if(observed==null || !attempt.workerId().equals(observed.workerInstanceId())
                    || !"FAILED".equals(observed.status())
                    || !("INTERNAL_ERROR".equals(observed.reasonCode()) || "DEPENDENCY_UNAVAILABLE".equals(observed.reasonCode())))continue;
            // Seal uses this same monitor. A durable receipt acquired before this decision wins.
            synchronized(delivery){ transactions.executeWithoutResult(tx->apply(attempt)); }
        }
    }

    private void apply(Attempt attempt) {
        var result=results.findForIngestion(attempt.analysisId()).orElse(null);
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        if(result==null || !Set.of(AnalysisStatus.PENDING,AnalysisStatus.PROCESSING).contains(result.getStatus())
                || !result.isForActiveRequest(attempt.requestId()) || !result.isForActiveExecution(attempt.executionId())
                || !attempt.workerId().toString().equals(result.getWorkerInstanceId())
                || result.getRecording().getDeletedAt()!=null || !Boolean.TRUE.equals(result.getRecording().getSelected())
                || result.getRecording().getTrainingSession().getStatus()!=org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING
                || result.getHandoffReceivedAt()!=null || result.getCanonicalResultEventId()!=null
                || result.getExecutionDeadlineAt()==null || !result.getExecutionDeadlineAt().isAfter(now)
                || result.getClaimExpiresAt()==null || !result.getClaimExpiresAt().isAfter(now))return;
        if(delivery.enabled() && (!delivery.operational() || delivery.entries().stream().anyMatch(e->
                e.document.projection().identity().executionId().equals(attempt.executionId()))))return;
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM analysis_canonical_handoffs WHERE execution_id=?)",
                Boolean.class,attempt.executionId())))return;
        result.fail("runpod_execution_failed", "분석 실행 중 오류가 발생했습니다. 현재 결과를 확인한 뒤 다시 시도해 주세요.", null, null);
    }

    @PreDestroy public void close(){lane.shutdownNow();}
}
