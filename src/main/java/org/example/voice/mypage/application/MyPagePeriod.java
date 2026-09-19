package org.example.voice.mypage.application;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;

import java.time.LocalDate;

enum MyPagePeriod {
    WEEK(6), MONTH(29), THREE_MONTHS(89), YEAR(364);

    private final int daysBefore;
    MyPagePeriod(int daysBefore) { this.daysBefore = daysBefore; }

    LocalDate from(LocalDate to) { return to.minusDays(daysBefore); }

    static MyPagePeriod parse(String value) {
        if (value == null || value.isBlank()) return MONTH;
        try { return valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new BaseException(ErrorCode.INVALID_INPUT_VALUE); }
    }
}
