package org.example.voice.training.infrastructure;

import org.example.voice.training.domain.entity.RecordingUploadIntent;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingUploadIntentSweeperTest {

    @Test
    void schedulesExpiredUnconsumedUploadForIdempotentDeletion() {
        RecordingUploadIntent intent = RecordingUploadIntent.issue(
                9L,
                7L,
                "recordings/users/9/sessions/7/source.webm",
                "audio/webm",
                1000L,
                OffsetDateTime.now().minusMinutes(1)
        );
        RecordingUploadIntentJpaRepository repository = mock(RecordingUploadIntentJpaRepository.class);
        RecordingDeletionScheduler scheduler = mock(RecordingDeletionScheduler.class);
        when(repository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                eq(RecordingUploadIntentStatus.ISSUED), any()
        )).thenReturn(List.of(intent));

        new RecordingUploadIntentSweeper(repository, scheduler).sweep();

        assertThat(intent.getStatus()).isEqualTo(RecordingUploadIntentStatus.EXPIRED);
        verify(scheduler).schedule(
                9L,
                7L,
                intent.getObjectKey(),
                RecordingDeletionReason.UPLOAD_EXPIRED
        );
    }
}
