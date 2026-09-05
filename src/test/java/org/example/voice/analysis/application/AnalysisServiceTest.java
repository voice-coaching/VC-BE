package org.example.voice.analysis.application;

import org.example.voice.analysis.domain.model.AnalysisResultData;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.type.AnalysisStatus;
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
        AnalysisResultData result = result(AnalysisStatus.PROCESSING);
        when(reader.findOwnedData(1L, 7L)).thenReturn(Optional.of(result));
        assertThatThrownBy(() -> new AnalysisService(reader).getCompleted(1L, 7L)).isInstanceOf(AnalysisNotCompletedException.class);
    }

    @Test void hidesOtherUsersResultAsNotFound() {
        when(reader.findOwnedData(1L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AnalysisService(reader).getCompleted(1L, 7L)).isInstanceOf(AnalysisNotFoundException.class);
    }

    @Test void reportsMissingSessionAnalysis() {
        when(reader.findLatestBySessionData(3L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AnalysisService(reader).getBySession(3L, 7L)).isInstanceOf(SessionAnalysisNotFoundException.class);
    }

    private AnalysisResultData result(AnalysisStatus status) {
        return new AnalysisResultData(1L, status, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
