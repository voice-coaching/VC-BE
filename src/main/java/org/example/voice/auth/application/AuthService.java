package org.example.voice.auth.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.model.AuthSession;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.port.PasswordHasher;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.port.UserWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserReader userReader;
    private final UserWriter userWriter;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final OnboardingProfileReader onboardingProfileReader;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) { return !userReader.existsByEmail(normalize(email)); }

    @Transactional
    public AuthSession signup(String email, String password, String nickname, boolean termsAgreed, boolean privacyAgreed) {
        if (!termsAgreed || !privacyAgreed) throw new AuthException(ErrorCode.VALIDATION_ERROR);
        String normalized = normalize(email);
        if (userReader.existsByEmail(normalized)) throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        User user;
        try {
            user = userWriter.save(User.createLocal(normalized, passwordHasher.hash(password), nickname.trim(), now));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        IssuedTokens tokens = tokenService.issueSession(user);
        return session(user, tokens, true, false);
    }

    @Transactional
    public AuthSession login(String email, String password) {
        User user = userReader.findByEmail(normalize(email))
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordHasher.matches(password, user.getPassword())) throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        if (user.isSuspended()) throw new AuthException(ErrorCode.USER_SUSPENDED);
        user.recordLogin(OffsetDateTime.now(ZoneOffset.UTC));
        userWriter.save(user);
        IssuedTokens tokens = tokenService.issueSession(user);
        return session(user, tokens, false, onboardingProfileReader.findByUserId(user.getId()).isPresent());
    }

    private AuthSession session(User user, IssuedTokens tokens, boolean newUser, boolean onboardingCompleted) {
        return new AuthSession(user, tokens.accessToken(), tokens.refreshToken(), tokenService.accessTokenSeconds(), newUser, onboardingCompleted);
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
