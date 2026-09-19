package org.example.voice.training.domain.entity;

import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.example.voice.common.exception.BaseException;

class TrainingSessionStateTest {

    @Test
    void repeatedCompletionPreservesFirstDurationAndTimestamp() {
        TrainingSession session = TrainingSession.create(9L, mock(PracticeContent.class), null, LearningFocus.PRONUNCIATION);
        session.startAnalysis();
        session.complete(120);
        var completedAt = session.getCompletedAt();
        session.complete(1);
        session.complete(null);
        assertThat(session.getTotalLearningSeconds()).isEqualTo(120);
        assertThat(session.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void completionRejectsUnanalyzedAndCanceledSessions() {
        TrainingSession session = TrainingSession.create(9L, mock(PracticeContent.class), null, LearningFocus.PRONUNCIATION);
        assertThatThrownBy(() -> session.complete(10)).isInstanceOf(BaseException.class);
        session.cancel();
        assertThatThrownBy(() -> session.complete(10)).isInstanceOf(BaseException.class);
        assertThat(session.getStatus()).isEqualTo(TrainingSessionStatus.CANCELED);
    }

    @Test
    void permitsOnlyDocumentedUploadAndAnalysisTransitions() {
        TrainingSession session = TrainingSession.create(
                9L,
                mock(PracticeContent.class),
                null,
                LearningFocus.PRONUNCIATION
        );

        assertThat(session.beginUpload()).isTrue();
        assertThat(session.getStatus()).isEqualTo(TrainingSessionStatus.UPLOADING);
        assertThat(session.beginUpload()).isTrue();
        assertThat(session.startAnalysis()).isTrue();
        assertThat(session.getStatus()).isEqualTo(TrainingSessionStatus.ANALYZING);
        assertThat(session.beginUpload()).isFalse();
        assertThat(session.startAnalysis()).isFalse();
        assertThat(session.allowsRecordingChanges()).isFalse();
        assertThat(session.allowsAnalysisRetry()).isTrue();
    }
}
