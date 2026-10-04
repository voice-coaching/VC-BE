package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.CanonicalCapabilitiesData;
import org.example.voice.analysis.domain.model.CanonicalCapabilitiesData.Scope;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class CanonicalCapabilitiesService {
    private final CanonicalHandoffReadiness readiness;
    private final org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions;

    public CanonicalCapabilitiesData read() {
        boolean open = readiness.admission();
        try { submissions.assertOpen(); }
        catch (org.example.voice.common.exception.BaseException unavailable) { open = false; }
        // Mirror CanonicalRequestScope's deliberately limited rollout. Never imply title grading.
        return new CanonicalCapabilitiesData("voice-coaching.analysis-capabilities.v1",
                RunPodContract.HANDOFF_PROFILE, open ? List.of(RunPodContract.RESULT_V5) : List.of(), open,
                Map.of("STANDALONE_AUDIO", new Scope(true, null),
                       "COURSE", new Scope(false, "COURSE_ANALYSIS_UNSUPPORTED"),
                       "TITLE_EXAM", new Scope(false, "TITLE_EXAM_ANALYSIS_UNSUPPORTED"),
                       "VIDEO", new Scope(false, "VIDEO_ANALYSIS_UNSUPPORTED")));
    }
}
