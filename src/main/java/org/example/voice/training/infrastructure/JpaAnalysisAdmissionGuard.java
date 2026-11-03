package org.example.voice.training.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.stream.AnalysisStreamProperties;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.AnalysisAdmissionGuard;
import org.example.voice.user.domain.entity.User;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaAnalysisAdmissionGuard implements AnalysisAdmissionGuard {

    private final EntityManager entityManager;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final AnalysisStreamProperties properties;

    @Override
    public void acquireAndAssertAvailable(Long userId) {}
}
