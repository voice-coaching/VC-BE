package org.example.voice.analysis.provider;

import org.example.voice.analysis.domain.type.FeedbackStyle;
import java.util.List;

public interface AiFeedbackProvider {
    GeneratedFeedback regenerate(FeedbackSource source, FeedbackStyle style);
    record FeedbackSource(
            String approvedSummaryFeedback,
            String selectedPhone,
            Integer selectedExpectedIndex,
            String evidenceState,
            String pipelineRevision
    ) {}
    record GeneratedFeedback(List<String> strengths, List<String> weaknesses, String summaryFeedback) {}
}
