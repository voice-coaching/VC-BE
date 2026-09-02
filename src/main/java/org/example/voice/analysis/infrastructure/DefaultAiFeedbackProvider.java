package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.type.FeedbackStyle;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DefaultAiFeedbackProvider implements AiFeedbackProvider {
    public GeneratedFeedback regenerate(FeedbackSource source, FeedbackStyle style) {
        if (source == null
                || source.approvedSummaryFeedback() == null
                || source.approvedSummaryFeedback().isBlank()
                || source.selectedPhone() == null
                || source.selectedPhone().isBlank()
                || source.selectedExpectedIndex() == null
                || source.selectedExpectedIndex() < 0
                || !"frozen_detector_threshold_passed".equals(source.evidenceState())
                || source.pipelineRevision() == null
                || source.pipelineRevision().isBlank()
                || style != FeedbackStyle.COACHING) {
            throw new IllegalArgumentException("approved pronunciation evidence is required");
        }
        // The default provider is deliberately deterministic: it may replay the
        // approved same-attempt claim, but it cannot invent a new diagnosis.
        return new GeneratedFeedback(List.of(), List.of(), source.approvedSummaryFeedback());
    }
}
