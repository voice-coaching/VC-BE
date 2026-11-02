package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.CanonicalResultCompletion;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument;
import org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackInbox;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fenced commit. No remote I/O and, pending post-processing approval, NO deletion calls. */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CanonicalCallbackCommitter {
    private final CanonicalCallbackInbox inbox;
    private final AnalysisResultReader results;
    private final AnalysisResultWriter writer;
    private final JdbcTemplate jdbc;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalBackendJournal journal;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalExecutionMediaStore executionMedia;

    @Transactional(timeout=5)
    public AnalysisResultIngestionDisposition commit(CanonicalCallbackDocument doc) { inbox.register(doc); return commitResult(doc,null); }

    /** Caller holds the handoff claim and visibility fence in this same transaction. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AnalysisResultIngestionDisposition commitHandoff(CanonicalCallbackDocument doc,java.util.UUID handoff) {

        jdbc.execute("SET LOCAL synchronous_commit = on");
        return commitResult(doc,handoff);
    }

    private AnalysisResultIngestionDisposition commitResult(CanonicalCallbackDocument doc,java.util.UUID handoff) {
        var id=doc.identity();
        var result=results.findForIngestion(id.analysisId())
                .orElseThrow(()->new RunPodContractException(404,"TARGET_NOT_FOUND"));

        // Video uses its existing recording deletion outbox. Canonical commit
        // never deletes MP4s or translates old segments into new visual evidence.

        var decision=doc.decision();
        jdbc.update("""
                INSERT INTO analysis_canonical_results
                    (event_id,execution_id,request_id,analysis_id,recording_id,content_id,worker_instance_id,
                     schema_version,analysis_profile,status,payload_sha256,raw_sha256,callback_bytes,callback_document,
                     evidence_receipt_id,core_sha256,canonical_analysis_id,representation,decision_status,
                     decision_reason_code,decision_stage,core_status,adapter_status,generation_status,handoff_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS json),?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (event_id) DO UPDATE SET execution_id=EXCLUDED.execution_id,request_id=EXCLUDED.request_id,analysis_id=EXCLUDED.analysis_id,recording_id=EXCLUDED.recording_id,content_id=EXCLUDED.content_id,worker_instance_id=EXCLUDED.worker_instance_id,schema_version=EXCLUDED.schema_version,analysis_profile=EXCLUDED.analysis_profile,status=EXCLUDED.status,payload_sha256=EXCLUDED.payload_sha256,raw_sha256=EXCLUDED.raw_sha256,callback_bytes=EXCLUDED.callback_bytes,callback_document=EXCLUDED.callback_document,evidence_receipt_id=EXCLUDED.evidence_receipt_id,core_sha256=EXCLUDED.core_sha256,canonical_analysis_id=EXCLUDED.canonical_analysis_id,representation=EXCLUDED.representation,decision_status=EXCLUDED.decision_status,decision_reason_code=EXCLUDED.decision_reason_code,decision_stage=EXCLUDED.decision_stage,core_status=EXCLUDED.core_status,adapter_status=EXCLUDED.adapter_status,generation_status=EXCLUDED.generation_status,handoff_id=EXCLUDED.handoff_id,applied_at=CURRENT_TIMESTAMP
""",id.eventId(),id.executionId(),id.requestId(),id.analysisId(),id.recordingId(),id.contentId(),id.workerId(),
                doc.schemaVersion(),doc.analysisProfile(),doc.status().name(),doc.payloadSha256(),doc.rawSha256(),
                doc.bytes(),doc.storageJson(),doc.retention()==null?null:doc.retention().receiptId(),doc.source().coreSha256(),
                doc.source().canonicalAnalysisId(),doc.representation(),decision==null?null:decision.status().name(),
                decision==null?null:decision.reasonCode(),decision==null?null:decision.stage(),doc.coreStatus(),
                doc.adapterStatus(),doc.generationStatus(),handoff);
        var completion=new CanonicalResultCompletion(id.eventId(),id.requestId(),id.executionId(),doc.payloadSha256(),
                doc.status(),doc.source().audioSha256(),doc.workerRevision(),doc.pipelineRevision(),summary(doc),
                doc.failure()==null?null:doc.failure().code(),doc.failure()==null?null:failureReason(),doc.overallScore());

        result.finishCanonical(completion);
        result.recordCanonicalCoachingDelivery(doc.decision() != null
                && doc.decision().status() == CanonicalCallbackDocument.DecisionStatus.ACCEPT
                && (("READY".equals(doc.adapterStatus()) && doc.feedbackDeliveryAllowed())
                    || doc.overallScore() != null));
        writer.save(result);
        var session=result.getRecording().getTrainingSession();
        jdbc.update("INSERT INTO analysis_canonical_result_effects(event_id,analysis_id,user_id,session_id) VALUES (?,?,?,?) ON CONFLICT (event_id) DO UPDATE SET analysis_id=EXCLUDED.analysis_id,user_id=EXCLUDED.user_id,session_id=EXCLUDED.session_id,completed_at=NULL",
                id.eventId(),id.analysisId(),session.getUserId(),session.getId());
        if(handoff==null) {
            inbox.markApplied(id.eventId());
            journal.recordAppliedAck(doc);
        } else {
            jdbc.update("INSERT INTO analysis_canonical_archive_jobs(handoff_id) VALUES (?)",handoff);
            jdbc.update("INSERT INTO analysis_canonical_archive_artifacts(handoff_id,kind) SELECT handoff_id,kind FROM analysis_canonical_handoff_artifacts WHERE handoff_id=?",handoff);
        }
        org.example.voice.common.logging.AnalysisLogEvents.afterCommit(log,
                doc.status()==AnalysisStatus.FAILED?"analysis_failed":"analysis_completed",id.analysisId(),
                id.requestId().toString(),id.executionId().toString(),doc.failure()==null?null:doc.failure().code());
        return AnalysisResultIngestionDisposition.APPLIED;
    }
    private static String summary(CanonicalCallbackDocument doc) {
        if (doc.feedback()!=null) return doc.feedback();
        if (!doc.coachingActions().isEmpty()) return String.join("\n",doc.coachingActions());
        return null;
    }
    private static String failureReason() { return "시스템에서 분석 결과를 안전하게 제공하지 못했습니다. 사용자 발음의 문제로 판단하지 않습니다."; }
}
