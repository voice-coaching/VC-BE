package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisResultData;
import org.example.voice.analysis.domain.model.AnalysisScoreBreakdown;
import org.example.voice.analysis.domain.model.PronunciationEvidenceData;
import org.example.voice.analysis.domain.model.VisualSupplementData;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnalysisResultReaderImpl implements AnalysisResultReader {

    private final AnalysisResultJpaRepository repository;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalReadFence canonicalReadFence;

    public Optional<AnalysisResult> findOwned(Long analysisId, Long userId) {
        if(!canonicalReadFence.visible(analysisId,userId))return Optional.empty();
        return repository.findByIdAndRecordingTrainingSessionUserId(analysisId, userId);
    }

    public Optional<AnalysisResult> findOwnedForUpdate(Long analysisId, Long userId) {
        // Acquire the existing analysis lock before checking mutable canonical visibility.
        return repository.findOwnedForUpdate(analysisId, userId)
                .filter(result -> canonicalReadFence.visible(analysisId,userId));
    }

    @Override
    public Optional<AnalysisResult> findForIngestion(Long analysisId) {
        return repository.findForIngestion(analysisId);
    }

    public Optional<AnalysisResult> findLatestBySession(Long sessionId, Long userId) {
        return repository
                .findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        sessionId,
                        userId
                );
    }

    @Override
    @Cacheable(
            cacheNames = AnalysisCacheNames.DETAIL,
            key = "T(org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys).owned(#p1, #p0)",
            condition = "!@canonicalReadFence.hasHistory(#p0)",
            unless = "#result == null || !#result.isCompleted()"
    )
    public Optional<AnalysisResultData> findOwnedData(Long analysisId, Long userId) {
        if(!canonicalReadFence.visible(analysisId,userId))return Optional.empty();
        return repository.findByIdAndRecordingTrainingSessionUserId(analysisId, userId)
                .map(this::toData);
    }

    @Override
    @Cacheable(
            cacheNames = AnalysisCacheNames.SESSION_RESULT,
            key = "T(org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys).session(#p1, #p0)",
            condition = "!@canonicalReadFence.sessionHasHistory(#p0, #p1)",
            unless = "#result == null || !#result.isCompleted()"
    )
    public Optional<AnalysisResultData> findLatestBySessionData(Long sessionId, Long userId) {
        return repository
                .findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        sessionId,
                        userId
                )
                .filter(result -> canonicalReadFence.visible(result.getId(),userId))
                .map(this::toData);
    }

    private AnalysisResultData toData(AnalysisResult result) {
        return new AnalysisResultData(
                result.getId(),
                result.getStatus(),
                result.getAnalysisOutcome(),
                result.getTranscript(),
                result.getSttConfidence(),
                result.getOverallScore(),
                result.getPronunciationScore(),
                result.getIntonationScore(),
                result.getSpeedWpm(),
                result.getSpeedStatus(),
                result.getStressScore(),
                result.getPauseScore(),
                result.getStrengthsText(),
                result.getWeaknessesText(),
                result.getSummaryFeedback(),
                pronunciationEvidence(result),
                visualSupplement(result),
                result.getAnalyzedAt(),
                AnalysisScoreBreakdown.fromAudit(result.getClovaScoreEvidence(), result.getOverallScore()),
                org.example.voice.analysis.domain.model.AnalysisScoreHierarchy.fromAudit(result.getClovaScoreEvidence(), result.getOverallScore()),
                result.getCoachingDocument()
        );
    }

    private VisualSupplementData visualSupplement(AnalysisResult result) {
        if (result.getVisualSupplementSchemaVersion() == null) {
            return null;
        }
        return new VisualSupplementData(
                result.getVisualSupplementSchemaVersion(),
                result.getSelectedExpectedIndex(),
                result.getVisualEvidenceRelation(),
                result.getVisualApprovedClaimId(),
                result.getVisualRendererKey(),
                result.getVisualPhoneAnchorRef(),
                result.getVisualSupplementSha256(),
                result.getVisualClosedBetaLipObservation()
        );
    }

    private PronunciationEvidenceData pronunciationEvidence(AnalysisResult result) {
        if (result.getPronunciationEvidenceSchemaVersion() == null) {
            return null;
        }
        return new PronunciationEvidenceData(
                result.getPronunciationEvidenceSchemaVersion(),
                result.getSelectedPhone(),
                result.getSelectedExpectedIndex(),
                result.getSelectedStartMs(),
                result.getSelectedEndMs(),
                result.getDetectorScore(),
                result.getOperatingThreshold(),
                result.getScoreSemantics(),
                result.getEvidenceState()
        );
    }
}
