package org.example.voice.mypage.application;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.mypage.domain.model.MyPageData;
import org.example.voice.mypage.domain.port.MyPageReader;
import org.example.voice.mypage.domain.port.MyPageWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyPageServiceTest {
    private MyPageReader reader;
    private MyPageWriter writer;
    private MyPageService service;

    @BeforeEach void setUp() {
        reader = mock(MyPageReader.class);
        writer = mock(MyPageWriter.class);
        service = new MyPageService(reader, writer);
    }

    @Test void historyDetailDistinguishesMissingAndForbiddenSession() {
        when(reader.findHistoryDetail(1L, 9L)).thenReturn(Optional.empty());
        when(reader.sessionExists(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.getHistoryDetail(1L, 9L)).isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRAINING_SESSION_ACCESS_DENIED);
    }

    @Test void deleteHistoryDeletesOwnedAggregate() {
        when(reader.sessionOwned(1L, 9L)).thenReturn(true);
        service.deleteHistory(1L, 9L);
        verify(writer).deleteHistory(9L);
    }

    @Test void deleteHistoryDoesNotDeleteForeignAggregate() {
        when(reader.sessionOwned(1L, 9L)).thenReturn(false);
        when(reader.sessionExists(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteHistory(1L, 9L)).isInstanceOf(BaseException.class);
        verify(writer, never()).deleteHistory(any());
    }

    @Test void strengthsAndWeaknessesExcludeInsufficientSamplesAndSortScores() {
        when(reader.findUnitScores(eq(1L), any(), any())).thenReturn(List.of(
                unit("A", "90", 4), unit("B", "55", 5), unit("C", "99", 2)));
        MyPageData.StrengthsWeaknesses result = service.getStrengthsWeaknesses(1L, "MONTH", 5);
        assertThat(result.minimumDataSatisfied()).isTrue();
        assertThat(result.strengths()).extracting(MyPageData.UnitScore::targetUnit).containsExactly("A");
        assertThat(result.weaknesses()).extracting(MyPageData.UnitScore::targetUnit).containsExactly("B");
    }

    @Test void scoreTrendRejectsUnsupportedMetric() {
        assertThatThrownBy(() -> service.getScoreTrend(1L, "SPEED", "MONTH")).isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    private MyPageData.UnitScore unit(String target, String score, int attempts) {
        return new MyPageData.UnitScore(target, target, new BigDecimal(score), attempts, null);
    }
    private MyPageData.HistoryDetail detail() {
        return new MyPageData.HistoryDetail(new MyPageData.Session(9L, "COMPLETED", OffsetDateTime.now(),
                OffsetDateTime.now(), 10), new MyPageData.Content(1L, "title", "script"),
                new MyPageData.Recording(2L, 1000, "PASS"), new MyPageData.Analysis(3L, "text",
                BigDecimal.TEN), List.of());
    }
}
