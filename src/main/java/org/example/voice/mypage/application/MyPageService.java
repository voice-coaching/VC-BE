package org.example.voice.mypage.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.mypage.domain.model.MyPageData;
import org.example.voice.mypage.domain.port.MyPageReader;
import org.example.voice.mypage.domain.port.MyPageWriter;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MyPageService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MINIMUM_ATTEMPTS = 3;
    private final MyPageReader reader;
    private final MyPageWriter writer;

    @Transactional(readOnly = true)
    public MyPageData.HistoryPage getHistory(Long userId, String type, String status, LocalDate from,
                                             LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
        Range range = range(from, to, null);
        return reader.findHistory(userId, enumValue(ContentType.class, type), enumValue(TrainingSessionStatus.class, status),
                range.from(), range.toExclusive(), page, size);
    }

    @Transactional(readOnly = true)
    public MyPageData.HistoryDetail getHistoryDetail(Long userId, Long sessionId) {
        return reader.findHistoryDetail(userId, sessionId).orElseThrow(() -> sessionError(sessionId));
    }

    @Transactional
    public void deleteHistory(Long userId, Long sessionId) {
        if (!reader.sessionOwned(userId, sessionId)) throw sessionError(sessionId);
        writer.deleteHistory(sessionId);
    }

    @Transactional(readOnly = true)
    public MyPageData.Statistics getStatistics(Long userId, String period, LocalDate from, LocalDate to) {
        Range range = range(from, to, period);
        LocalDate today = LocalDate.now(SEOUL);
        return reader.calculateStatistics(userId, range.from(), range.toExclusive(), start(today), start(today.plusDays(1)));
    }

    @Transactional(readOnly = true)
    public MyPageData.StrengthsWeaknesses getStrengthsWeaknesses(Long userId, String period, int limit) {
        if (limit < 1 || limit > 20) throw invalid();
        Range range = range(null, null, period);
        List<MyPageData.UnitScore> eligible = reader.findUnitScores(userId, range.from(), range.toExclusive()).items().stream()
                .filter(it -> it.attemptCount() >= MINIMUM_ATTEMPTS).toList();
        List<MyPageData.UnitScore> strengths = eligible.stream()
                .filter(it -> it.averageScore().compareTo(java.math.BigDecimal.valueOf(80)) >= 0)
                .sorted(Comparator.comparing(MyPageData.UnitScore::averageScore).reversed()).limit(limit).toList();
        List<MyPageData.UnitScore> weaknesses = eligible.stream()
                .filter(it -> it.averageScore().compareTo(java.math.BigDecimal.valueOf(70)) < 0)
                .sorted(Comparator.comparing(MyPageData.UnitScore::averageScore)).limit(limit).toList();
        return new MyPageData.StrengthsWeaknesses(strengths, weaknesses, !eligible.isEmpty());
    }

    @Transactional(readOnly = true)
    public MyPageData.ScoreTrend getScoreTrend(Long userId, String metric, String period) {
        String normalized = metric == null ? "" : metric.toUpperCase();
        if (!List.of("OVERALL", "PRONUNCIATION", "INTONATION").contains(normalized)) throw invalid();
        Range range = range(null, null, period);
        return new MyPageData.ScoreTrend(normalized,
                reader.findScoreTrend(userId, normalized, range.from(), range.toExclusive()).items());
    }

    @Transactional(readOnly = true)
    public MyPageData.WeaknessRecommendations getWeaknessRecommendations(Long userId, int limit, String type) {
        if (limit < 1 || limit > 50) throw invalid();
        Range range = range(null, null, "MONTH");
        List<MyPageData.UnitScore> units = reader.findUnitScores(userId, range.from(), range.toExclusive()).items().stream()
                .filter(it -> it.attemptCount() >= MINIMUM_ATTEMPTS)
                .sorted(Comparator.comparing(MyPageData.UnitScore::averageScore)).limit(5).toList();
        List<MyPageData.Weakness> weaknesses = units.stream().map(it -> new MyPageData.Weakness(
                it.targetUnit(), it.label(), it.averageScore().setScale(1, RoundingMode.HALF_UP))).toList();
        return new MyPageData.WeaknessRecommendations(weaknesses,
                reader.findRecommendations(units.stream().map(MyPageData.UnitScore::targetUnit).toList(),
                        enumValue(ContentType.class, type), limit).items());
    }

    private BaseException sessionError(Long sessionId) {
        return new BaseException(reader.sessionExists(sessionId)
                ? ErrorCode.TRAINING_SESSION_ACCESS_DENIED : ErrorCode.TRAINING_SESSION_NOT_FOUND);
    }
    private BaseException invalid() { return new BaseException(ErrorCode.INVALID_INPUT_VALUE); }
    private Range range(LocalDate from, LocalDate to, String period) {
        LocalDate end = to == null ? LocalDate.now(SEOUL) : to;
        LocalDate begin = from == null ? MyPagePeriod.parse(period).from(end) : from;
        if (begin.isAfter(end)) throw invalid();
        return new Range(begin, end, start(begin), start(end.plusDays(1)));
    }
    private OffsetDateTime start(LocalDate date) { return date.atStartOfDay(SEOUL).toOffsetDateTime(); }
    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase()); }
        catch (IllegalArgumentException exception) { throw invalid(); }
    }
    private record Range(LocalDate startDate, LocalDate endDate, OffsetDateTime from, OffsetDateTime toExclusive) {}
}
