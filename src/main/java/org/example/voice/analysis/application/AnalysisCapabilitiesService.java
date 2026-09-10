package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.AnalysisCapabilitiesData;
import org.example.voice.analysis.domain.port.AnalysisCapabilitiesReader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisCapabilitiesService {
    private final AnalysisCapabilitiesReader reader;

    public AnalysisCapabilitiesData getCapabilities() {
        return reader.read();
    }
}
