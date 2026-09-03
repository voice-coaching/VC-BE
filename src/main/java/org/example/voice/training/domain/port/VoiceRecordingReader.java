package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.RecordingPlaybackUrlData;
import org.example.voice.training.domain.model.AnalysisJobRequestData;
import org.example.voice.training.domain.model.VoiceRecordingData;
import org.example.voice.training.domain.type.RecordingQualityStatus;

import java.util.List;
import java.util.Optional;

public interface VoiceRecordingReader {

    boolean existsByObjectKey(String objectKey);

    int countBySessionId(Long sessionId);

    List<VoiceRecordingData> findBySessionId(Long sessionId, Long userId);

    Optional<RecordingQualityStatus> findQualityStatus(Long sessionId, Long recordingId, Long userId);

    boolean isSelected(Long sessionId, Long recordingId, Long userId);

    boolean hasCompletedAnalysis(Long recordingId);

    Optional<Long> findSelectedRecordingId(Long sessionId, Long userId);

    Optional<RecordingQualityStatus> findSelectedRecordingQualityStatus(Long sessionId, Long userId);

    Optional<RecordingPlaybackUrlData> findPlaybackUrl(Long recordingId, Long userId);

    Optional<AnalysisJobRequestData> findAnalysisJobRequest(Long analysisId, Long sessionId, Long recordingId, Long userId);
}
