package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.port.AnalysisSegmentReader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisSegmentService {

    private final AnalysisSegmentReader analysisSegmentReader;
}
