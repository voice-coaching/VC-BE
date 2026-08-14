package org.example.voice.analysis.provider;

import org.example.voice.analysis.domain.type.FeedbackStyle;
import java.util.List;

public interface AiFeedbackProvider {
    GeneratedFeedback regenerate(FeedbackSource source, FeedbackStyle style);
    record FeedbackSource(String transcript, List<String> strengths, List<String> weaknesses) {}
    record GeneratedFeedback(List<String> strengths, List<String> weaknesses, String summaryFeedback) {}
}
