package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.type.OAuthProvider;

public interface SocialOAuthProvider {
    OAuthProvider provider();
    SocialUserInfo authenticate(String authorizationCode, String redirectUri);
}
