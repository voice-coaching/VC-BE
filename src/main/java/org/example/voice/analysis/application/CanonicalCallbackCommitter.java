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
public class CanonicalCallbackCommitter {
    private final CanonicalCallbackInbox inbox;
    private final AnalysisResultReader results;
    private final AnalysisResultWriter writer;
    private final JdbcTemplate jdbc;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalBackendJournal journal;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalExecutionMediaStore executionMedia;

    @Transactional(timeout=5)
    public AnalysisResultIngestionDisposition commit(CanonicalCallbackDocument doc) {
        if(inbox.register(doc)==CanonicalCallbackInbox.State.APPLIED)
            return AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
        var id=doc.identity();
        inbox.requireVerifiedForCommit(doc);
        return commitResult(doc,null);
    }

    /** Caller holds the handoff claim and visibility fence in this same transaction. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AnalysisResultIngestionDisposition commitHandoff(CanonicalCallbackDocument doc,java.util.UUID handoff) {
        if(!RunPodContract.handoffResult(doc.schemaVersion()))throw new RunPodContractException(422,"VALIDATION_FAILED");
        jdbc.execute("SET LOCAL synchronous_commit = on");
        return commitResult(doc,handoff);
    }

    private AnalysisResultIngestionDisposition commitResult(CanonicalCallbackDocument doc,java.util.UUID handoff) {
        var id=doc.identity();
        var result=results.findForIngestion(id.analysisId())
                .orElseThrow(()->new RunPodContractException(404,"TARGET_NOT_FOUND"));
        if(result.getLastResultEventId()!=null)throw new RunPodContractException(409,"RESULT_ALREADY_FINALIZED");
        if (RunPodContract.RESULT_V6.equals(doc.schemaVersion())) executionMedia.requireResult(doc);
        // Video uses its existing recording deletion outbox. Canonical commit
        // never deletes MP4s or translates old segments into new visual evidence.
        if((result.getRecording().getVisualObjectKey()!=null && !RunPodContract.RESULT_V6.equals(doc.schemaVersion())) || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM analysis_segments WHERE analysis_result_id=?)",Boolean.class,id.analysisId())))
            throw new RunPodContractException(503,"NOT_READY");
        var decision=doc.decision();
        jdbc.update("""
                INSERT INTO analysis_canonical_results
                    (event_id,execution_id,request_id,analysis_id,recording_id,content_id,worker_instance_id,
                     schema_version,analysis_profile,status,payload_sha256,raw_sha256,callback_bytes,callback_document,
                     evidence_receipt_id,core_sha256,canonical_analysis_id,representation,decision_status,
                     decision_reason_code,decision_stage,core_status,adapter_status,generation_status,handoff_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS json),?,?,?,?,?,?,?,?,?,?,?)
                """,id.eventId(),id.executionId(),id.requestId(),id.analysisId(),id.recordingId(),id.contentId(),id.workerId(),
                doc.schemaVersion(),doc.analysisProfile(),doc.status().name(),doc.payloadSha256(),doc.rawSha256(),
                doc.bytes(),doc.storageJson(),doc.retention()==null?null:doc.retention().receiptId(),doc.source().coreSha256(),
                doc.source().canonicalAnalysisId(),doc.representation(),decision==null?null:decision.status().name(),
                decision==null?null:decision.reasonCode(),decision==null?null:decision.stage(),doc.coreStatus(),
                doc.adapterStatus(),doc.generationStatus(),handoff);
        var completion=new CanonicalResultCompletion(id.eventId(),id.requestId(),id.executionId(),doc.payloadSha256(),
                doc.status(),doc.source().audioSha256(),doc.workerRevision(),doc.pipelineRevision(),summary(doc),
                doc.failure()==null?null:doc.failure().code(),doc.failure()==null?null:failureReason(),doc.overallScore());
        if(!result.finishCanonical(completion))throw new RunPodContractException(409,"RESULT_ALREADY_FINALIZED");
        writer.save(result);
        var session=result.getRecording().getTrainingSession();
        jdbc.update("INSERT INTO analysis_canonical_result_effects(event_id,analysis_id,user_id,session_id) VALUES (?,?,?,?)",
                id.eventId(),id.analysisId(),session.getUserId(),session.getId());
        if(handoff==null) {
            inbox.markApplied(id.eventId());
            journal.recordAppliedAck(doc);
        } else {
            jdbc.update("INSERT INTO analysis_canonical_archive_jobs(handoff_id) VALUES (?)",handoff);
            jdbc.update("INSERT INTO analysis_canonical_archive_artifacts(handoff_id,kind) SELECT handoff_id,kind FROM analysis_canonical_handoff_artifacts WHERE handoff_id=?",handoff);
        }
        return AnalysisResultIngestionDisposition.APPLIED;
    }
    private static String summary(CanonicalCallbackDocument doc) {
        if(doc.status()==AnalysisStatus.FAILED)return failureReason();
        if ("READY".equals(doc.adapterStatus()) && doc.feedback()!=null) return doc.feedback();
        // This commit path follows independent semantic verification. H5's
        // actions already satisfy max3, single-sentence and UTF-16 140 limits.
        if(doc.decision().status()==CanonicalCallbackDocument.DecisionStatus.ACCEPT
                && doc.feedbackDeliveryAllowed() && "READY".equals(doc.adapterStatus())
                && java.util.Set.of("9f10296b6944249ded9f5ce2ccfdfa7c53b6e4af670999fe68e9e477e97eaf45",
                    "820d600fab4c49615050b9a038e7236f0429f68249dc631a39c0f5ab0be6f0f6",
                    "4afc1c2cdbf7b5440742cf154fe96ab2083beda69cd9e07c47a241fb0d784fcf",
                    "e3d6d6be9d896ede8d75d5f35f5daf4520a8d42f484e5f84a6fc32f8a05fb2e3",
                    "885a2f0a67494dc3bebed28962222bfe95a0143292fe2de04194b84ea8beadc5",
                    "358365399f7445ab0a77797ce46619979955503c4faac8185724e2225aa5205c").contains(doc.source().llmManifestSha256())
                && !doc.coachingActions().isEmpty()) {
            return String.join("\n",doc.coachingActions());
        }
        return switch(doc.decision().status()) {
            case ACCEPT -> doc.overallScore()!=null
                ? "분석과 근거 기반 채점을 완료했습니다. 이번 분석에서 안내할 수 있는 발음 연습 후보는 없습니다."
                : "분석을 완료했습니다. 상세 근거는 새 분석 결과 화면에서 확인해 주세요. 발음이 정상이라는 판정이나 점수는 제공하지 않습니다.";
            case REJECT -> "입력이 분석 조건을 충족하지 않아 교정을 제공하지 않았습니다. 입력을 확인한 뒤 다시 녹음해 주세요.";
            case INCONCLUSIVE -> "근거가 충분하지 않아 판단을 보류했습니다. 상세 안내를 확인한 뒤 다시 녹음해 주세요.";
            case SYSTEM_FAILURE -> failureReason();
        };
    }
    private static String failureReason() { return "시스템에서 분석 결과를 안전하게 제공하지 못했습니다. 사용자 발음의 문제로 판단하지 않습니다."; }
}
