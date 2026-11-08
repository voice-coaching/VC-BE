package org.example.voice.training.domain.entity;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

class RecordingUploadRecoveryStateTest {
    @Test void aCommittedRecordingCannotBeExpiredBySessionOrUserCleanup() {
        var intent = RecordingUploadIntent.issue(9L, 7L, "fixture", "audio/wav", 123L, OffsetDateTime.now().plusMinutes(10));
        intent.consume(50L, "a".repeat(64));
        assertThat(intent.expire()).isFalse();
        assertThat(intent.getRecordingId()).isEqualTo(50L);
        assertThat(intent.getStatus().name()).isEqualTo("CONSUMED");
    }

    @Test void repeatedExpirationCannotRescheduleTheSameIntent() {
        var intent = RecordingUploadIntent.issue(9L, 7L, "fixture", "audio/wav", 123L, OffsetDateTime.now().minusSeconds(1));
        assertThat(intent.canRegisterAt(OffsetDateTime.now())).isFalse();
        assertThat(intent.expire()).isTrue();
        assertThat(intent.expire()).isFalse();
    }
}
