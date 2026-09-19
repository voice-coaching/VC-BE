package org.example.voice.practicecontent.domain.port;

import org.example.voice.practicecontent.controller.dto.PracticeContentNextConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.domain.model.PracticeContentDetailData;
import org.example.voice.practicecontent.domain.model.PracticeContentPageData;
import org.example.voice.practicecontent.domain.model.PracticeContentRecommendationListData;
import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;

import java.util.Optional;

public interface PracticeContentReader {

    PracticeContentPageData<PracticeContentSummaryData> findPracticeContents(PracticeContentQueryConditionDto condition);

    Optional<PracticeContentDetailData> findPracticeContent(Long contentId);

    Optional<PracticeContentSummaryData> findNextPracticeContent(PracticeContentNextConditionDto condition);

    Optional<PracticeContentRecommendationListData> findRecommendationsByContentId(Long contentId);
}
