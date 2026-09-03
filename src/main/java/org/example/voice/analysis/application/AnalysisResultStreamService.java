package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisResultStreamData;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.port.AnalysisSegmentWriter;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisResultStreamService {

    private final AnalysisResultWriter analysisResultWriter;
    private final AnalysisSegmentWriter analysisSegmentWriter;
    private final TrainingSessionWriter trainingSessionWriter;

    @Transactional
    public void applyResult(AnalysisResultStreamData data) {
        AnalysisResult analysisResult = analysisResultWriter.findByIdForUpdate(data.analysisId())
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));

        if (data.status() == AnalysisStatus.PROCESSING) {
            if (!analysisResult.canStartProcessing()) {
                return;
            }
            analysisResult.markProcessing();
            analysisResultWriter.save(analysisResult);
            return;
        }

        if (data.status() == AnalysisStatus.COMPLETED) {
            if (!analysisResult.canFinish()) {
                return;
            }
            analysisResult.complete(
                    data.transcript(),
                    data.sttConfidence(),
                    data.sttModelName(),
                    data.overallScore(),
                    data.pronunciationScore(),
                    data.intonationScore(),
                    data.speedWpm(),
                    data.speedStatus(),
                    data.stressScore(),
                    data.pauseScore(),
                    data.strengthsText(),
                    data.weaknessesText(),
                    data.summaryFeedback()
            );
            analysisSegmentWriter.replaceSegments(analysisResult, data.segments());
            analysisResultWriter.save(analysisResult);
            return;
        }

        if (data.status() == AnalysisStatus.FAILED) {
            if (!analysisResult.canFinish()) {
                return;
            }
            analysisResult.fail(data.failureReason());
            trainingSessionWriter.updateStatus(
                    analysisResult.getRecording().getTrainingSession().getId(),
                    TrainingSessionStatus.FAILED
            );
            analysisResultWriter.save(analysisResult);
            return;
        }

        throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
