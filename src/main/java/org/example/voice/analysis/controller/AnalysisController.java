package org.example.voice.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.AnalysisService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;
}
