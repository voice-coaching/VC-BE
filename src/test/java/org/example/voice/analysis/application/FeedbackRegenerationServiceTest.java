package org.example.voice.analysis.application;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.type.FeedbackStyle;
import org.example.voice.analysis.exception.FeedbackRegenerationLimitException;
import org.example.voice.analysis.exception.FeedbackEvidenceUnavailableException;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackRegenerationServiceTest {
    @Mock AnalysisResultReader reader;
    @Mock AnalysisResultWriter writer;
    @Mock AiFeedbackProvider provider;

    @Test void regeneratesAndPersistsFeedback() {
        AnalysisResult result = mock(AnalysisResult.class);
        when(reader.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(result));
        when(result.isCompleted()).thenReturn(true);
        when(result.getFeedbackRegenerationCount()).thenReturn(0);
        when(result.getAnalysisOutcome()).thenReturn(AnalysisOutcome.COACHING_READY);
        when(result.getSummaryFeedback()).thenReturn("승인된 피드백");
        when(result.getSelectedPhone()).thenReturn("ㄱ");
        when(result.getSelectedExpectedIndex()).thenReturn(0);
        when(result.getEvidenceState()).thenReturn("frozen_detector_threshold_passed");
        when(result.getPipelineRevision()).thenReturn("seungun-v1");
        when(provider.regenerate(any(), eq(FeedbackStyle.COACHING))).thenReturn(new AiFeedbackProvider.GeneratedFeedback(List.of("강점"), List.of("약점"), "요약"));
        when(writer.save(result)).thenReturn(result);
        new FeedbackRegenerationService(reader, writer, provider).regenerate(1L, 7L, FeedbackStyle.COACHING);
        verify(result).regenerateFeedback(eq("강점"), eq("약점"), eq("요약"), any());
        verify(writer).save(result);
    }

    @Test void rejectsFourthRegenerationBeforeCallingProvider() {
        AnalysisResult result = mock(AnalysisResult.class);
        when(reader.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(result));
        when(result.isCompleted()).thenReturn(true);
        when(result.getFeedbackRegenerationCount()).thenReturn(3);
        FeedbackRegenerationService service = new FeedbackRegenerationService(reader, writer, provider);
        assertThatThrownBy(() -> service.regenerate(1L, 7L, FeedbackStyle.COACHING)).isInstanceOf(FeedbackRegenerationLimitException.class);
        verifyNoInteractions(provider, writer);
    }

    @Test void rejectsRegenerationWithoutSameAttemptPronunciationEvidence() {
        AnalysisResult result = mock(AnalysisResult.class);
        when(reader.findOwnedForUpdate(1L, 7L)).thenReturn(Optional.of(result));
        when(result.isCompleted()).thenReturn(true);
        when(result.getFeedbackRegenerationCount()).thenReturn(0);
        when(result.getAnalysisOutcome()).thenReturn(AnalysisOutcome.COACHING_READY);

        FeedbackRegenerationService service = new FeedbackRegenerationService(reader, writer, provider);

        assertThatThrownBy(() -> service.regenerate(1L, 7L, FeedbackStyle.COACHING))
                .isInstanceOf(FeedbackEvidenceUnavailableException.class);
        verifyNoInteractions(provider, writer);
    }
}
