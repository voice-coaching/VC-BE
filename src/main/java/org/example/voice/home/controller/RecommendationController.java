package org.example.voice.home.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.application.RecommendationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
}
