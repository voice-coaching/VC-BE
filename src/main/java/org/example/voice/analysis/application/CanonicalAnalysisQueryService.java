package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.ServiceFailure;
import org.example.voice.analysis.domain.port.CanonicalExecutionRegistry;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.exception.CanonicalAnalysisViewException;
import org.example.voice.analysis.infrastructure.canonical.*;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.example.voice.analysis.exception.CanonicalAnalysisViewException.Reason.*;

@Service
@RequiredArgsConstructor
public class CanonicalAnalysisQueryService {
    private final AnalysisResultJpaRepository results;
    private final CanonicalExecutionRegistry executions;
    private final CanonicalCommittedResultReader committed;
    private final CanonicalViewVisibility visibility;
    private final CanonicalPublicProjection projection;
    private final CanonicalActionPolicy actions;
    private final CanonicalDeliverySpool delivery;
    private final CanonicalPublishedResults published;

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=10)
    public CanonicalAnalysisView get(Long analysisId,Long userId) {
        if(analysisId==null || analysisId<1 || analysisId>9007199254740991L)throw error(INVALID_ANALYSIS_ID);
        var direct=published.current(analysisId,userId);
        if(direct!=null){
            var id=direct.identity();var f=direct.failure();var reason=java.util.List.of("RESULT_PERSISTENCE_PENDING");
            var unavailable=new CanonicalAnalysisView.UnavailableReasons(reason,reason,reason,reason);
            return new CanonicalAnalysisView("voice-coaching.canonical-analysis-view.v2",analysisId,id.recordingId(),
                id.requestId(),id.executionId(),direct.status(),direct.analysisProfile(),projection.project(direct),
                f==null?null:new ServiceFailure(f.origin(),f.code(),f.stage()),
                new CanonicalAnalysisView.Actions(false,false,false,false,unavailable),"SAVING");
        }
        if(!visibility.visible(analysisId,userId))throw error(RESOURCE_NOT_FOUND);
        var result=results.findByIdAndRecordingTrainingSessionUserId(analysisId,userId)
                .orElseThrow(()->error(RESOURCE_NOT_FOUND));
        if(!result.isCanonicalExecution()) {
            stable(result,userId);
            if(!"LEGACY_SEUNGUN_V3".equals(result.getAnalysisProfile()) || result.getCanonicalResultEventId()!=null)
                throw error(CANONICAL_RESULT_UNAVAILABLE);
            throw error(CANONICAL_ANALYSIS_NOT_FOUND);
        }
        if (!RunPodContract.HANDOFF_PROFILE.equals(result.getAnalysisProfile()))
            throw error(CANONICAL_ANALYSIS_NOT_FOUND);
        var binding=executions.findCurrentForOwner(analysisId,userId).orElse(null);
        if(binding==null) {
            stable(result,userId);
            throw error(CANONICAL_RESULT_UNAVAILABLE);
        }
        if(!result.isForActiveRequest(binding.requestId()) || !result.isForActiveExecution(binding.executionId())
                || !result.getRecording().getId().equals(binding.recordingId())
                || !result.getRecording().getTrainingSession().getContent().getId().equals(binding.contentId())
                || !Objects.equals(result.getRecording().getAudioSha256(),binding.audioSha256())
                || !binding.resultSchemaVersion().equals(result.getExpectedResultSchemaVersion())
                || !RunPodContract.RESULT_V5.equals(binding.resultSchemaVersion())
                || !RunPodContract.REQUEST_V3.equals(binding.requestSchemaVersion())) {
            stable(result,userId);
            throw error(CANONICAL_RESULT_UNAVAILABLE);
        }
        boolean pending=result.getStatus()==AnalysisStatus.PENDING || result.getStatus()==AnalysisStatus.PROCESSING;
        var preview=delivery.current(result);
        if(pending && preview!=null && preview.verified){
            var d=preview.document.projection();var f=d.failure();
            var reason=java.util.List.of("RESULT_PERSISTENCE_PENDING");
            var unavailable=new CanonicalAnalysisView.UnavailableReasons(reason,reason,reason,reason);
            var view=new CanonicalAnalysisView("voice-coaching.canonical-analysis-view.v2",analysisId,binding.recordingId(),
                binding.requestId(),binding.executionId(),d.status(),result.getAnalysisProfile(),projection.project(d),
                f==null?null:new ServiceFailure(f.origin(),f.code(),f.stage()),
                new CanonicalAnalysisView.Actions(false,false,false,false,unavailable),preview.attempts>0?"RETRYING":"SAVING");
            stable(result,userId);return view;
        }
        CanonicalCallbackDocument document=null;
        if(result.getCanonicalResultEventId()!=null) {
            document=committed.findCurrent(result).orElse(null);
            if(pending || document==null) {
                stable(result,userId);
                throw error(CANONICAL_RESULT_UNAVAILABLE);
            }
        } else if(result.getStatus()==AnalysisStatus.COMPLETED || result.getLastResultEventId()!=null) {
            stable(result,userId);
            throw error(CANONICAL_RESULT_UNAVAILABLE);
        }
        var canonical=document==null?null:projection.project(document);
        ServiceFailure failure=null;
        if(result.getStatus()==AnalysisStatus.FAILED) {
            if(document==null) failure=backendFailure(result.getFailureCode());
            else {
                var f=document.failure();
                if(f==null)throw error(CANONICAL_RESULT_UNAVAILABLE);
                failure=new ServiceFailure(f.origin(),f.code(),f.stage());
            }
        }
        var view=new CanonicalAnalysisView(RunPodContract.RESULT_V5.equals(binding.resultSchemaVersion())?"voice-coaching.canonical-analysis-view.v2":CanonicalAnalysisView.SCHEMA_VERSION,analysisId,binding.recordingId(),
                binding.requestId(),binding.executionId(),result.getStatus(),result.getAnalysisProfile(),
                canonical,failure,actions.current(result),delivery.enabled()?(document==null?"NONE":"SAVED"):null);
        stable(result,userId);
        return view;
    }

    private void stable(AnalysisResult result,Long userId) {
        // READ_COMMITTED gives these uncached SQL checks fresh visibility after projection work.
        if(!visibility.visible(result.getId(),userId))throw error(RESOURCE_NOT_FOUND);
        if(!visibility.unchanged(result,userId)) {
            if(!visibility.visible(result.getId(),userId))throw error(RESOURCE_NOT_FOUND);
            throw error(CANONICAL_ANALYSIS_CHANGED);
        }
    }

    private static ServiceFailure backendFailure(String code) {
        if("runpod_execution_failed".equals(code))return new ServiceFailure("RUNPOD","CANONICAL_EXECUTION_FAILED","EXECUTION");
        // Do not echo arbitrary historic failureReason/code/exception text into the new public contract.
        if("analysis_execution_timeout".equals(code))return new ServiceFailure("BACKEND","ANALYSIS_EXECUTION_TIMEOUT","EXECUTION");
        if("runpod_analysis_request_delivery_failed".equals(code))return new ServiceFailure("BACKEND","ANALYSIS_DELIVERY_FAILED","DISPATCH");
        return new ServiceFailure("BACKEND","ANALYSIS_FAILED","EXECUTION");
    }
    private static CanonicalAnalysisViewException error(CanonicalAnalysisViewException.Reason reason) {
        return new CanonicalAnalysisViewException(reason);
    }
}
