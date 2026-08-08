package org.example.voice.home.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.application.HomeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;
}
