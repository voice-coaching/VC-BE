package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.training.domain.port.RecordingMediaPreparationStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Snapshot an already checked server preparation within the admission transaction. */
@Repository
public class CanonicalExecutionMediaStore {
    private final JdbcTemplate jdbc;
    private final RecordingMediaPreparationStore preparations;
    private final org.example.voice.analysis.infrastructure.runpod.RunPodContract contract;
    public CanonicalExecutionMediaStore(JdbcTemplate jdbc, RecordingMediaPreparationStore preparations,
            org.example.voice.analysis.infrastructure.runpod.RunPodContract contract) {
        this.jdbc = jdbc; this.preparations = preparations; this.contract = contract;
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void snapshot(UUID execution, long recording, String audio, String video) {
        var preparation = preparations.find(recording);
        // Old audio recordings predate receipts. Do not fabricate/backfill evidence.
        if (preparation.isEmpty()) {

            return;
        }

        jdbc.update("""
            INSERT INTO analysis_execution_media(execution_id,recording_id,media_type,
                audio_sha256,video_sha256,receipt_sha256,receipt_bytes)
            SELECT ?,p.recording_id,?,?,?,?,p.receipt_bytes FROM recording_media_preparations p
            WHERE p.recording_id=? AND EXISTS (
                SELECT 1 FROM analysis_canonical_executions e
                WHERE e.execution_id=? AND e.recording_id=p.recording_id AND e.audio_sha256=?) ON CONFLICT (execution_id) DO UPDATE SET recording_id=EXCLUDED.recording_id,media_type=EXCLUDED.media_type,audio_sha256=EXCLUDED.audio_sha256,video_sha256=EXCLUDED.video_sha256,receipt_sha256=EXCLUDED.receipt_sha256,receipt_bytes=EXCLUDED.receipt_bytes
""", execution, video == null ? "AUDIO_ONLY" : "AUDIO_VISUAL", audio, video,
                receiptSha(recording), recording, execution, audio);
        Boolean matches = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM analysis_execution_media e
                JOIN recording_media_preparations p ON p.recording_id=e.recording_id
                WHERE e.execution_id=? AND e.recording_id=? AND e.audio_sha256=?
                  AND e.video_sha256 IS NOT DISTINCT FROM CAST(? AS varchar)
                  AND e.receipt_sha256=p.receipt_sha256 AND e.receipt_bytes=p.receipt_bytes)
            """, Boolean.class, execution, recording, audio, video);

    }

    private String receiptSha(long recording) {
        return jdbc.queryForObject("SELECT receipt_sha256 FROM recording_media_preparations WHERE recording_id=?",
                String.class, recording);
    }

    public void requireRequest(UUID execution, com.fasterxml.jackson.databind.JsonNode request) {}

    public void requireResult(CanonicalCallbackDocument document) {}

    private static String nullable(com.fasterxml.jackson.databind.JsonNode node) {
        return node==null || node.isNull()?null:node.asText();
    }

}
