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

    @Transactional(timeout=5)
    public AnalysisResultIngestionDisposition commit(CanonicalCallbackDocument doc) {
        if(inbox.register(doc)==CanonicalCallbackInbox.State.APPLIED)
            return AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
        var id=doc.identity();
        inbox.requireVerifiedForCommit(doc);
        var result=results.findForIngestion(id.analysisId())
                .orElseThrow(()->new RunPodContractException(404,"TARGET_NOT_FOUND"));
        if(result.getLastResultEventId()!=null)throw new RunPodContractException(409,"RESULT_ALREADY_FINALIZED");
        // Temporary explicit blocker, not a substitute media-retention policy.
        // The deletion-outbox hook was denied by safety review and awaits user approval.
        // Do not silently skip cleanup, erase old segments, or acknowledge these attempts.
        if(result.getRecording().getVisualObjectKey()!=null || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM analysis_segments WHERE analysis_result_id=?)",Boolean.class,id.analysisId())))
            throw new RunPodContractException(503,"NOT_READY");
        var decision=doc.decision();
        jdbc.update("""
                INSERT INTO analysis_canonical_results
                    (event_id,execution_id,request_id,analysis_id,recording_id,content_id,worker_instance_id,
                     schema_version,analysis_profile,status,payload_sha256,raw_sha256,callback_bytes,callback_document,
                     evidence_receipt_id,core_sha256,canonical_analysis_id,representation,decision_status,
                     decision_reason_code,decision_stage,core_status,adapter_status,generation_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS json),?,?,?,?,?,?,?,?,?,?)
                """,id.eventId(),id.executionId(),id.requestId(),id.analysisId(),id.recordingId(),id.contentId(),id.workerId(),
                RunPodContract.RESULT_V4,"CANONICAL_FROZEN_20260928_V4",doc.status().name(),doc.payloadSha256(),doc.rawSha256(),
                doc.bytes(),doc.storageJson(),doc.retention()==null?null:doc.retention().receiptId(),doc.source().coreSha256(),
                doc.source().canonicalAnalysisId(),doc.representation(),decision==null?null:decision.status().name(),
                decision==null?null:decision.reasonCode(),decision==null?null:decision.stage(),doc.coreStatus(),
                doc.adapterStatus(),doc.generationStatus());
        var completion=new CanonicalResultCompletion(id.eventId(),id.requestId(),id.executionId(),doc.payloadSha256(),
                doc.status(),doc.source().audioSha256(),doc.workerRevision(),doc.pipelineRevision(),summary(doc),
                doc.failure()==null?null:doc.failure().code(),doc.failure()==null?null:failureReason(),doc.overallScore());
        if(!result.finishCanonical(completion))throw new RunPodContractException(409,"RESULT_ALREADY_FINALIZED");
        writer.save(result);
        var session=result.getRecording().getTrainingSession();
        jdbc.update("INSERT INTO analysis_canonical_result_effects(event_id,analysis_id,user_id,session_id) VALUES (?,?,?,?)",
                id.eventId(),id.analysisId(),session.getUserId(),session.getId());
        inbox.markApplied(id.eventId());
        journal.recordAppliedAck(doc);
        return AnalysisResultIngestionDisposition.APPLIED;
    }
    private static String summary(CanonicalCallbackDocument doc) {
        if(doc.status()==AnalysisStatus.FAILED)return failureReason();
        // This commit path follows independent semantic verification. H5's
        // actions already satisfy max3, single-sentence and UTF-16 140 limits.
        if(doc.decision().status()==CanonicalCallbackDocument.DecisionStatus.ACCEPT
                && doc.feedbackDeliveryAllowed() && "READY".equals(doc.adapterStatus())
                && java.util.Set.of("9f10296b6944249ded9f5ce2ccfdfa7c53b6e4af670999fe68e9e477e97eaf45",
                    "820d600fab4c49615050b9a038e7236f0429f68249dc631a39c0f5ab0be6f0f6",
                    "4afc1c2cdbf7b5440742cf154fe96ab2083beda69cd9e07c47a241fb0d784fcf").contains(doc.source().llmManifestSha256())
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
