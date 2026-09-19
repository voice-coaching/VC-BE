package org.example.voice.training.infrastructure;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingAnalysisWriterImplTest {

    @Test
    void persistsRetryCountOnTheReusedAnalysisRow() {
        AnalysisResult result = failedResult();
        AnalysisResultJpaRepository repository = mock(AnalysisResultJpaRepository.class);
        when(repository.findForIngestion(5L)).thenReturn(Optional.of(result));
        TrainingAnalysisWriterImpl writer = new TrainingAnalysisWriterImpl(
                mock(VoiceRecordingJpaRepository.class),
                repository
        );

        var retried = writer.retry(5L, UUID.randomUUID());

        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    @Test
    void refusesARequestAfterThreePersistedRetries() {
        AnalysisResult result = failedResult();
        for (int retry = 0; retry < 3; retry++) {
            result.retry(UUID.randomUUID());
            result.fail("temporary", "retryable", "worker-v1", "pipeline-v1");
        }
        AnalysisResultJpaRepository repository = mock(AnalysisResultJpaRepository.class);
        when(repository.findForIngestion(5L)).thenReturn(Optional.of(result));
        TrainingAnalysisWriterImpl writer = new TrainingAnalysisWriterImpl(
                mock(VoiceRecordingJpaRepository.class),
                repository
        );

        assertThatThrownBy(() -> writer.retry(5L, UUID.randomUUID()))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MAX_RETRY_EXCEEDED));
        assertThat(result.getRetryCount()).isEqualTo(3);
    }

    private static AnalysisResult failedResult() {
        AnalysisResult result = AnalysisResult.pending(mock(VoiceRecording.class), UUID.randomUUID());
        result.fail("temporary", "retryable", "worker-v1", "pipeline-v1");
        return result;
    }
}
