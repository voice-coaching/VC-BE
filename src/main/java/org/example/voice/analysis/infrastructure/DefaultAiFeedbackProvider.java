package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.type.FeedbackStyle;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DefaultAiFeedbackProvider implements AiFeedbackProvider {
    public GeneratedFeedback regenerate(FeedbackSource source, FeedbackStyle style) {
        List<String> strengths = source.strengths().isEmpty() ? List.of("문장을 끝까지 안정적으로 발화했습니다.") : source.strengths();
        List<String> weaknesses = source.weaknesses().isEmpty() ? List.of("핵심 음절을 한 번 더 또박또박 연습해 보세요.") : source.weaknesses();
        return new GeneratedFeedback(strengths, weaknesses, "강점은 유지하면서 개선 항목을 천천히 반복해 보세요.");
    }
}
