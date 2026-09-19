package org.example.voice.training.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.stream.AnalysisStreamProperties;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaAnalysisAdmissionGuardTest {

    @Test
    void serializesOnUserRowBeforeEnforcingConcurrentLimit() {
        EntityManager entityManager = mock(EntityManager.class);
        AnalysisResultJpaRepository results = mock(AnalysisResultJpaRepository.class);
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        User user = mock(User.class);
        when(entityManager.find(User.class, 9L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);
        when(results.countByRecordingTrainingSessionUserIdAndStatusIn(
                9L,
                List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING)
        )).thenReturn(3L);

        assertThatThrownBy(() -> new JpaAnalysisAdmissionGuard(
                entityManager, results, properties
        ).acquireAndAssertAvailable(9L))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.ANALYSIS_CONCURRENT_LIMIT_EXCEEDED));

        verify(entityManager).find(User.class, 9L, LockModeType.PESSIMISTIC_WRITE);
    }
}
