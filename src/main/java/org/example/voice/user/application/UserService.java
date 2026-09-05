package org.example.voice.user.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.analysis.domain.port.AnalysisCancellation;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.model.UpdatedUserProfile;
import org.example.voice.user.domain.model.UserProfile;
import org.example.voice.user.domain.model.WithdrawalResult;
import org.example.voice.user.domain.port.LoginProviderReader;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.port.UserSessionRevoker;
import org.example.voice.user.domain.port.UserWriter;
import org.example.voice.user.exception.NicknameAlreadyExistsException;
import org.example.voice.user.exception.UserNotFoundException;
import org.example.voice.user.exception.WithdrawalAlreadyProcessedException;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final LoginProviderReader loginProviderReader;
    private final UserSessionRevoker userSessionRevoker;
    private final OnboardingProfileReader onboardingProfileReader;
    private final ProcessingConsentLedger processingConsentLedger;
    private final RecordingDeletionScheduler recordingDeletionScheduler;
    private final RecordingUploadIntentRegistry uploadIntentRegistry;
    private final AnalysisCancellation analysisCancellation;

    @Transactional(readOnly = true)
    public UserProfile getMyProfile(Long userId) {
        User user = findUser(userId);
        LinkedHashSet<String> loginProviders = new LinkedHashSet<>();
        if (user.hasLocalCredential()) {
            loginProviders.add("LOCAL");
        }
        loginProviders.addAll(loginProviderReader.findByUserId(userId));

        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getStatus(),
                List.copyOf(loginProviders),
                onboardingProfileReader.findByUserId(userId).isPresent(),
                user.getCreatedAt()
        );
    }

    @Transactional
    public UpdatedUserProfile updateMyProfile(Long userId, String nickname) {
        User user = userReader.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
        String normalizedNickname = nickname.trim();
        if (userReader.existsByNicknameExcludingUserId(normalizedNickname, userId)) {
            throw new NicknameAlreadyExistsException();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.updateNickname(normalizedNickname, now);
        userWriter.save(user);
        return new UpdatedUserProfile(user.getId(), user.getNickname(), user.getUpdatedAt());
    }

    @Transactional
    public WithdrawalResult withdraw(Long userId) {
        User user = userReader.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
        if (user.isWithdrawn()) {
            throw new WithdrawalAlreadyProcessedException();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.withdraw(now);
        userWriter.save(user);
        userSessionRevoker.revokeAll(userId);
        analysisCancellation.cancelForUser(userId);
        processingConsentLedger.revokeForUser(userId);
        recordingDeletionScheduler.scheduleAllForUser(userId, RecordingDeletionReason.USER_WITHDRAWN);
        uploadIntentRegistry.expireForUser(userId);
        return new WithdrawalResult(now);
    }

    private User findUser(Long userId) {
        return userReader.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
