package org.example.voice.training.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.example.voice.training.domain.port.TrainingSessionReader;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingCompletionPersistenceTest {
    final TrainingSessionJpaRepository sessions=mock(TrainingSessionJpaRepository.class);
    final TrainingAnalysisReader analysis=mock(TrainingAnalysisReader.class);
    final TrainingSession session=TrainingSession.create(9L,mock(PracticeContent.class),null,LearningFocus.PRONUNCIATION);
    final TrainingSessionWriterImpl writer=new TrainingSessionWriterImpl(null,sessions,null,null,null,null,analysis,null,null);

    @BeforeEach void setup() {
        when(sessions.findByIdForUpdate(7L)).thenReturn(Optional.of(session));
        session.startAnalysis();
    }

    @Test void pendingResultCannotCompleteLearningEvenIfTheClientAlreadySawFeedback() {
        assertThatThrownBy(() -> writer.complete(7L,20)).isInstanceOf(BaseException.class);
        assertThat(session.getStatus()).isEqualTo(TrainingSessionStatus.ANALYZING);
        assertThat(session.getCompletedAt()).isNull();
    }

    @Test void committedCurrentResultAllowsCompletionAndReplayKeepsFirstCompletion() {
        when(analysis.existsCompletedAnalysisForSelectedRecording(7L,9L)).thenReturn(true);
        var first=writer.complete(7L,20);
        assertThat(session.getStatus()).isEqualTo(TrainingSessionStatus.COMPLETED);
        assertThat(writer.complete(7L,99)).isEqualTo(first);
        assertThat(session.getTotalLearningSeconds()).isEqualTo(20);
        verify(analysis,times(1)).existsCompletedAnalysisForSelectedRecording(7L,9L);
    }

    @Test void canceledSessionCannotBeResurrectedByACompletedAnalysis() {
        session.cancel();
        assertThatThrownBy(() -> writer.complete(7L,20)).isInstanceOf(BaseException.class);
        assertThat(session.getStatus()).isEqualTo(TrainingSessionStatus.CANCELED);
        verifyNoInteractions(analysis);
    }

    @Test void completionChecksOwnershipBeforeCallingTheMutationPort() {
        var reader=mock(TrainingSessionReader.class);var mutations=mock(TrainingSessionWriter.class);
        var service=new TrainingSessionService(reader,mutations,analysis,null,null,null,null,null);
        assertThatThrownBy(() -> service.complete(7L,20,8L)).isInstanceOf(BaseException.class);
        verifyNoInteractions(mutations);
    }
}
