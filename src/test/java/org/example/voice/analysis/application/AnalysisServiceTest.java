package org.example.voice.analysis.application;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.exception.AnalysisNotCompletedException;
import org.example.voice.analysis.exception.AnalysisNotFoundException;
import org.example.voice.analysis.exception.SessionAnalysisNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {
    @Mock AnalysisResultReader reader;

    @Test void returnsOnlyCompletedOwnedResult() {
        AnalysisResult result = mock(AnalysisResult.class);
        when(reader.findOwned(1L, 7L)).thenReturn(Optional.of(result));
        when(result.isCompleted()).thenReturn(false);
        assertThatThrownBy(() -> new AnalysisService(reader).getCompleted(1L, 7L)).isInstanceOf(AnalysisNotCompletedException.class);
    }

    @Test void hidesOtherUsersResultAsNotFound() {
        when(reader.findOwned(1L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AnalysisService(reader).getCompleted(1L, 7L)).isInstanceOf(AnalysisNotFoundException.class);
    }

    @Test void reportsMissingSessionAnalysis() {
        when(reader.findLatestBySession(3L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AnalysisService(reader).getBySession(3L, 7L)).isInstanceOf(SessionAnalysisNotFoundException.class);
    }
}
