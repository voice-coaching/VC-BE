package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.entity.SocialAccount;

public interface SocialAccountWriter {
    SocialAccount save(SocialAccount socialAccount);
}
