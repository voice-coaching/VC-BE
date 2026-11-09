package org.example.voice.payment.infrastructure;

import java.time.OffsetDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.model.PremiumTrial;
import org.example.voice.payment.domain.port.PremiumTrialStore;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPremiumTrialStore implements PremiumTrialStore {
    private final NamedParameterJdbcTemplate jdbc;

    @Override public Optional<PremiumTrial> find(Long userId) {
        return Optional.ofNullable(findAll(List.of(userId)).get(userId));
    }

    @Override public Map<Long, PremiumTrial> findAll(Collection<Long> ids) {
        var result = new HashMap<Long, PremiumTrial>();
        if (ids.isEmpty()) return result;
        jdbc.query("SELECT user_id, starts_at, expires_at FROM user_premium_trials WHERE user_id IN (:ids)",
                Map.of("ids", ids), (rs, row) -> new PremiumTrial(rs.getLong("user_id"),
                        rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class)))
                .forEach(trial -> result.put(trial.userId(), trial));
        return result;
    }

    /** Caller holds the users row lock. The PK also prevents a second lifetime activation. */
    @Override public void insert(PremiumTrial trial) {
        jdbc.update("INSERT INTO user_premium_trials(user_id, starts_at, expires_at) VALUES (:user, :start, :end)",
                Map.of("user", trial.userId(), "start", trial.startsAt(), "end", trial.expiresAt()));
    }
}
