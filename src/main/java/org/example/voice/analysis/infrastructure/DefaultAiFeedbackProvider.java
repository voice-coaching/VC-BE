package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.type.FeedbackStyle;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DefaultAiFeedbackProvider implements AiFeedbackProvider {
    public GeneratedFeedback regenerate(FeedbackSource source, FeedbackStyle style) {

        // The default provider is deliberately deterministic: it may replay the
        // approved same-attempt claim, but it cannot invent a new diagnosis.
        return new GeneratedFeedback(List.of(), List.of(), source.approvedSummaryFeedback());
    }
}
