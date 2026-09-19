package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.type.FeedbackStyle;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAiFeedbackProviderTest {

    private final DefaultAiFeedbackProvider provider = new DefaultAiFeedbackProvider();

    @Test
    void replaysOnlyTheApprovedSameAttemptMessage() {
        String approved = "목표 음소 ‘ㄱ’ 소리를 천천히 분리해 발음해 보세요.";

        AiFeedbackProvider.GeneratedFeedback generated = provider.regenerate(
                new AiFeedbackProvider.FeedbackSource(
                        approved,
                        "ㄱ",
                        0,
                        "frozen_detector_threshold_passed",
                        "g2pk:2.0.0|seungun:frozen-v1"
                ),
                FeedbackStyle.COACHING
        );

        assertThat(generated.summaryFeedback()).isEqualTo(approved);
        assertThat(generated.strengths()).isEmpty();
        assertThat(generated.weaknesses()).isEmpty();
    }

    @Test
    void refusesToCreateFeedbackWithoutApprovedEvidence() {
        assertThatThrownBy(() -> provider.regenerate(
                new AiFeedbackProvider.FeedbackSource(
                        "승인되지 않은 문구",
                        null,
                        null,
                        null,
                        null
                ),
                FeedbackStyle.COACHING
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
