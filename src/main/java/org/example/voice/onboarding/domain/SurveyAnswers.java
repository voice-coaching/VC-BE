package org.example.voice.onboarding.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.onboarding.controller.dto.SurveyAnswersRequestDto;
import org.example.voice.onboarding.controller.dto.SurveyAnswersUpdateRequestDto;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyAnswers {

    private List<String> learningPurposes;
    private List<String> improvementAreas;
    private List<String> pronunciationConcerns;
    private List<String> learningSituations;

    public static SurveyAnswers from(SurveyAnswersRequestDto request) {
        return new SurveyAnswers(
                request.learningPurposes(),
                request.improvementAreas(),
                request.pronunciationConcerns(),
                request.learningSituations()
        );
    }

    public void patch(SurveyAnswersUpdateRequestDto request) {
        if (request.learningPurposes() != null) {
            this.learningPurposes = request.learningPurposes();
        }
        if (request.improvementAreas() != null) {
            this.improvementAreas = request.improvementAreas();
        }
        if (request.pronunciationConcerns() != null) {
            this.pronunciationConcerns = request.pronunciationConcerns();
        }
        if (request.learningSituations() != null) {
            this.learningSituations = request.learningSituations();
        }
    }
}
