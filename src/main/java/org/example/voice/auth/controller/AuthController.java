package org.example.voice.auth.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.application.AuthService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
}
