package org.example.voice.user.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.user.domain.UserReader;
import org.example.voice.user.domain.UserWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
}
