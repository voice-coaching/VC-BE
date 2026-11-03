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
    public org.example.voice.analysis.domain.model.CanonicalResultContract contract(Long analysisId, Long userId) {
        // Apply the same ownership, visibility and current-generation fences as
        // detail reads, including verified previews. Then read the stored tuple.
        var view = get(analysisId, userId);
        var binding = executions.findCurrentForOwner(analysisId, userId)
                .orElseThrow(() -> error(CANONICAL_RESULT_UNAVAILABLE));

        return new org.example.voice.analysis.domain.model.CanonicalResultContract(
                view.analysisId(), binding.recordingId(), binding.requestId(), binding.executionId(),
                view.analysisProfile(), binding.resultSchemaVersion());
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=10)
    public CanonicalAnalysisView get(Long analysisId,Long userId) {
        var result=results.findById(analysisId).orElseThrow(()->error(RESOURCE_NOT_FOUND));
        var document=published.current(analysisId,userId);
        if(document==null) document=committed.findCurrent(result).orElse(null);
        if(document==null) {
            var preview=delivery.current(result);
            if(preview!=null) document=preview.document.projection();
        }
        var binding=executions.findCurrentForOwner(analysisId,userId).orElse(null);
        var id=document==null?null:document.identity();
        var failure=document==null?null:document.failure();
        return new CanonicalAnalysisView(RunPodContract.viewSchema(result.getAnalysisProfile()),analysisId,
                result.getRecording().getId(),id!=null?id.requestId():binding==null?null:binding.requestId(),
                id!=null?id.executionId():binding==null?null:binding.executionId(),
                document==null?result.getStatus():document.status(),result.getAnalysisProfile(),
                document==null?null:projection.project(document),
                failure==null?null:new ServiceFailure(failure.origin(),failure.code(),failure.stage()),actions.current(result),
                document==null?"NONE":"AVAILABLE").withRawResult(document==null?null:
                new tools.jackson.databind.ObjectMapper().readValue(document.storageJson(),java.util.Map.class));
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
