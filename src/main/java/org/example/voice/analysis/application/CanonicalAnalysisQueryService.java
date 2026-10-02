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

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=10)
    public CanonicalAnalysisView get(Long analysisId,Long userId) {
        if(analysisId==null || analysisId<1 || analysisId>9007199254740991L)throw error(INVALID_ANALYSIS_ID);
        if(!visibility.visible(analysisId,userId))throw error(RESOURCE_NOT_FOUND);
        var result=results.findByIdAndRecordingTrainingSessionUserId(analysisId,userId)
                .orElseThrow(()->error(RESOURCE_NOT_FOUND));
        if(!result.isCanonicalExecution()) {
            stable(result,userId);
            if(!"LEGACY_SEUNGUN_V3".equals(result.getAnalysisProfile()) || result.getCanonicalResultEventId()!=null)
                throw error(CANONICAL_RESULT_UNAVAILABLE);
            throw error(CANONICAL_ANALYSIS_NOT_FOUND);
        }
        var binding=executions.findCurrentForOwner(analysisId,userId).orElse(null);
        if(binding==null) {
            stable(result,userId);
            throw error(CANONICAL_RESULT_UNAVAILABLE);
        }
        if(!result.isForActiveRequest(binding.requestId()) || !result.isForActiveExecution(binding.executionId())
                || !result.getRecording().getId().equals(binding.recordingId())
                || !result.getRecording().getTrainingSession().getContent().getId().equals(binding.contentId())
                || !Objects.equals(result.getRecording().getAudioSha256(),binding.audioSha256())
                || !RunPodContract.RESULT_V4.equals(result.getExpectedResultSchemaVersion())
                || !RunPodContract.RESULT_V4.equals(binding.resultSchemaVersion())
                || !RunPodContract.REQUEST_V2.equals(binding.requestSchemaVersion())) {
            stable(result,userId);
            throw error(CANONICAL_RESULT_UNAVAILABLE);
        }
        boolean pending=result.getStatus()==AnalysisStatus.PENDING || result.getStatus()==AnalysisStatus.PROCESSING;
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
        var view=new CanonicalAnalysisView(CanonicalAnalysisView.SCHEMA_VERSION,analysisId,binding.recordingId(),
                binding.requestId(),binding.executionId(),result.getStatus(),CanonicalAnalysisView.PROFILE,
                canonical,failure,actions.current(result));
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
        // Do not echo arbitrary historic failureReason/code/exception text into the new public contract.
        if("analysis_execution_timeout".equals(code))return new ServiceFailure("BACKEND","ANALYSIS_EXECUTION_TIMEOUT","EXECUTION");
        if("runpod_analysis_request_delivery_failed".equals(code))return new ServiceFailure("BACKEND","ANALYSIS_DELIVERY_FAILED","DISPATCH");
        return new ServiceFailure("BACKEND","ANALYSIS_FAILED","EXECUTION");
    }
    private static CanonicalAnalysisViewException error(CanonicalAnalysisViewException.Reason reason) {
        return new CanonicalAnalysisViewException(reason);
    }
}
