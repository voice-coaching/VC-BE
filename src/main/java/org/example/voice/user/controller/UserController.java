package org.example.voice.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.user.application.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
}
