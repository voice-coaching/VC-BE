package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisRetryPolicy;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.Actions;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.UnavailableReasons;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.stream.AnalysisStreamProperties;
import org.example.voice.training.application.TrainingAnalysisSchemaAdmissionService;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Read-only UI hints. The actual commands still acquire locks and repeat authorization/state checks. */
@Component
@RequiredArgsConstructor
public final class CanonicalActionPolicy {
    private final CanonicalCompletionEligibility completion;
    private final CanonicalRerecordEligibility rerecordEligibility;
    private final TrainingAnalysisSchemaAdmissionService admission;
    private final CanonicalRequestScope scope;
    private final AnalysisResultJpaRepository results;
    private final AnalysisStreamProperties concurrency;
    private final RunPodAnalysisProperties runpod;

    public Actions current(AnalysisResult result) {
        return new Actions(true, true, true, true,
                new UnavailableReasons(List.of(), List.of(), List.of(), List.of()));
    }
}
