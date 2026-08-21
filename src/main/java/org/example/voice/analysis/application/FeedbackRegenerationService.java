package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.type.FeedbackStyle;
import org.example.voice.analysis.exception.AnalysisNotCompletedException;
import org.example.voice.analysis.exception.AnalysisNotFoundException;
import org.example.voice.analysis.exception.FeedbackRegenerationLimitException;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.analysis.provider.AiFeedbackProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackRegenerationService {
    private static final int LIMIT = 3;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AnalysisResultReader reader;
    private final AnalysisResultWriter writer;
    private final AiFeedbackProvider provider;

    @Transactional
    @CacheEvict(
            cacheNames = AnalysisCacheNames.DETAIL,
            key = "T(org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys).owned(#userId, #analysisId)"
    )
    public AnalysisResult regenerate(Long analysisId, Long userId, FeedbackStyle style) {
        AnalysisResult result = reader.findOwnedForUpdate(analysisId, userId).orElseThrow(AnalysisNotFoundException::new);
        if (!result.isCompleted()) throw new AnalysisNotCompletedException();
        if (result.getFeedbackRegenerationCount() >= LIMIT) throw new FeedbackRegenerationLimitException();
        AiFeedbackProvider.GeneratedFeedback generated = provider.regenerate(new AiFeedbackProvider.FeedbackSource(result.getTranscript(), split(result.getStrengthsText()), split(result.getWeaknessesText())), style);
        result.regenerateFeedback(String.join("\n", generated.strengths()), String.join("\n", generated.weaknesses()), generated.summaryFeedback(), OffsetDateTime.now(SEOUL));
        return writer.save(result);
    }

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\R")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }
}
