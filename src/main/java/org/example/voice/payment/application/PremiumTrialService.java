package org.example.voice.payment.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.payment.domain.MembershipReader;
import org.example.voice.payment.domain.model.PremiumTrial;
import org.example.voice.payment.domain.port.PremiumTrialStore;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PremiumTrialService {
    private final PremiumTrialStore trials;
    private final MembershipReader memberships;
    private final UserReader users;
    private final Clock clock;

    public record Status(String status, boolean eligible, OffsetDateTime startsAt, OffsetDateTime expiresAt) {}

    @Transactional(readOnly = true)
    public Status status(Long userId) {
        requireActive(users.findById(userId).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND)));
        var now = OffsetDateTime.now(clock);
        var trial = trials.find(userId);
        if (trial.isPresent()) return describe(trial.get(), now);
        boolean eligible = "free".equals(memberships.read(userId).type());
        return new Status(eligible ? "ELIGIBLE" : "INELIGIBLE", eligible, null, null);
    }

    @Transactional
    public Status activate(Long userId) {
        // Serialize with concurrent activations and payment updates across all application instances.
        requireActive(users.findByIdForUpdate(userId).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND)));
        var now = OffsetDateTime.now(clock);
        var existing = trials.find(userId);
        if (existing.isPresent()) {
            if (existing.get().active(now)) return describe(existing.get(), now);
            throw new BaseException(ErrorCode.PREMIUM_TRIAL_ALREADY_USED);
        }
        if (!"free".equals(memberships.read(userId).type()))
            throw new BaseException(ErrorCode.PREMIUM_TRIAL_NOT_ELIGIBLE);
        var trial = new PremiumTrial(userId, now, now.plusHours(72));
        trials.insert(trial);
        return describe(trial, now);
    }

    private Status describe(PremiumTrial trial, OffsetDateTime now) {
        return new Status(trial.active(now) ? "ACTIVE" : "EXPIRED", false, trial.startsAt(), trial.expiresAt());
    }

    private void requireActive(User user) {
        if (user.isWithdrawn()) throw new BaseException(ErrorCode.USER_WITHDRAWN);
        if (user.isSuspended()) throw new BaseException(ErrorCode.USER_SUSPENDED);
    }
}
