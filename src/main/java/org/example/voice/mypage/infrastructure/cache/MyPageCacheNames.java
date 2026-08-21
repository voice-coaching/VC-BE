package org.example.voice.mypage.infrastructure.cache;

public final class MyPageCacheNames {

    public static final String HISTORY = "mypage:history";
    public static final String HISTORY_DETAIL = "mypage:history-detail";
    public static final String STATISTICS = "mypage:statistics";
    public static final String UNIT_SCORES = "mypage:unit-scores";
    public static final String SCORE_TREND = "mypage:score-trend";
    public static final String RECOMMENDATIONS = "mypage:recommendations";

    public static final String[] ALL = {
            HISTORY,
            HISTORY_DETAIL,
            STATISTICS,
            UNIT_SCORES,
            SCORE_TREND,
            RECOMMENDATIONS
    };

    private MyPageCacheNames() {
    }
}
