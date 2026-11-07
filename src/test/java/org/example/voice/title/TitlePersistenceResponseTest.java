package org.example.voice.title;

import org.example.voice.title.controller.TitleExceptionHandler;
import org.example.voice.title.domain.TitleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TitlePersistenceResponseTest {
    @Test void pendingAndFinalMissingScoreHaveDistinctStableHttpCodes() {
        var handler=new TitleExceptionHandler();
        var pending=handler.title(new TitleException(409,"ANALYSIS_NOT_COMPLETED"));
        var missing=handler.title(new TitleException(409,"ANALYSIS_SCORE_UNAVAILABLE"));
        assertThat(pending.getStatusCode().value()).isEqualTo(409);
        assertThat(missing.getStatusCode().value()).isEqualTo(409);
        assertThat(pending.getBody().getCode()).isEqualTo("ANALYSIS_NOT_COMPLETED");
        assertThat(missing.getBody().getCode()).isEqualTo("ANALYSIS_SCORE_UNAVAILABLE");
        assertThat(pending.getBody().getMessage()).contains("분석 완료 후");
    }
}
