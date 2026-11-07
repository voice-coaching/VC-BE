package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.CanonicalCapabilitiesData;
import org.example.voice.analysis.domain.model.CanonicalCapabilitiesData.Scope;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired)
public class CanonicalCapabilitiesService {
    private final CanonicalHandoffReadiness readiness;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness audiovisual;
    private final org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions;
    private final org.example.voice.analysis.infrastructure.independent.IndependentAnalysisProperties independent;
    public CanonicalCapabilitiesService(CanonicalHandoffReadiness readiness,
            org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness audiovisual,
            org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions) {
        this(readiness,audiovisual,submissions,new org.example.voice.analysis.infrastructure.independent.IndependentAnalysisProperties());
    }

    public CanonicalCapabilitiesData read() {
        boolean open = independent.isSubmissionEnabled()?independent.acceptsSubmissions():readiness.admission();
        try { submissions.assertOpen(); }
        catch (org.example.voice.common.exception.BaseException unavailable) { open = false; }
        boolean videoOpen = !independent.isSubmissionEnabled() && open && audiovisual.admission();
        // Pronunciation courses bind trusted education revisions; focus support is advertised separately.
        return new CanonicalCapabilitiesData("voice-coaching.analysis-capabilities.v1",
                RunPodContract.AUDIOVISUAL_PROFILE, open ? List.of(RunPodContract.RESULT_V6) : List.of(), open,
                Map.of("STANDALONE_AUDIO", new Scope(true, null),
                       "COURSE", new Scope(true, null),
                       "TITLE_EXAM", new Scope(true, null),
                       "VIDEO", new Scope(videoOpen, videoOpen ? null : "VIDEO_ANALYSIS_UNSUPPORTED")));
    }
}
