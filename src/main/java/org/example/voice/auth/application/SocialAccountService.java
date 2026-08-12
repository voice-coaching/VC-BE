package org.example.voice.auth.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.model.AuthSession;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.port.SocialAccountReader;
import org.example.voice.auth.domain.port.SocialAccountWriter;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.port.UserWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
class SocialAccountService {
    private final SocialAccountReader socialAccountReader;
    private final SocialAccountWriter socialAccountWriter;
    private final UserReader userReader;
    private final UserWriter userWriter;
    private final TokenService tokenService;
    private final OnboardingProfileReader onboardingProfileReader;

    @Transactional
    public AuthSession completeLogin(OAuthProvider provider, SocialUserInfo profile) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SocialAccount account = socialAccountReader.findByProviderAndProviderUserId(provider, profile.providerUserId()).orElse(null);
        boolean newUser = account == null;
        User user;
        if (account != null) {
            user = userReader.findById(account.getUserId()).orElseThrow(() -> new AuthException(ErrorCode.INVALID_AUTHORIZATION_CODE));
        } else {
            String email = profile.email() == null ? null : profile.email().toLowerCase(Locale.ROOT);
            user = email == null ? null : userReader.findByEmail(email).orElse(null);
            if (user == null) {
                String nickname = profile.nickname() == null || profile.nickname().isBlank()
                        ? provider.name().toLowerCase(Locale.ROOT) + "_user" : profile.nickname();
                user = userWriter.save(User.createSocial(email, nickname, now));
            }
            socialAccountWriter.save(SocialAccount.create(user.getId(), provider, profile.providerUserId(), email, now));
        }
        if (user.isSuspended()) throw new AuthException(ErrorCode.USER_SUSPENDED);
        user.recordLogin(now);
        userWriter.save(user);
        IssuedTokens tokens = tokenService.issueSession(user);
        boolean onboardingCompleted = onboardingProfileReader.findByUserId(user.getId()).isPresent();
        return new AuthSession(user, tokens.accessToken(), tokens.refreshToken(), tokenService.accessTokenSeconds(), newUser, onboardingCompleted);
    }
}
