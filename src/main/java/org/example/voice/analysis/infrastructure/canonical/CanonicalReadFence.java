package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Canonical and subsequent retry generations must never trust an old legacy cache entry. */
@Component("canonicalReadFence")
@RequiredArgsConstructor
public class CanonicalReadFence {
    private final JdbcTemplate jdbc;
    public boolean hasHistory(Long analysisId) { return false; }
    public boolean sessionHasHistory(Long sessionId,Long userId) { return false; }
    public boolean visible(Long analysisId,Long userId) { return true; }
}
