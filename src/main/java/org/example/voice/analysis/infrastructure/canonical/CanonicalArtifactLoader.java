package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ordered, exact-version artifact reads shared by the two verification lanes.
 * The caller owns the durable claim and supplies its live execution fence.
 * This component neither retries nor opens transactions, caches originals,
 * verifies frozen semantics, or promotes a receipt/callback to VERIFIED.
 */
@Component
final class CanonicalArtifactLoader {
    private final RunPodContract contract;
    private final CanonicalEvidenceReader reader;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final java.util.concurrent.ExecutorService reads = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(10), runnable -> {
                var thread = new Thread(runnable, "canonical-artifact-read");
                thread.setDaemon(true); return thread;
            });

    CanonicalArtifactLoader(RunPodContract contract, CanonicalEvidenceReader reader,
                            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.contract = contract;
        this.reader = reader;
        this.jdbc = jdbc;
    }

    List<byte[]> load(byte[] manifestBytes, long analysisId, UUID executionId, Runnable fence) {
        return load(manifestBytes, analysisId, executionId, fence, false);
    }

    List<byte[]> loadForCallback(byte[] manifestBytes, long analysisId, UUID executionId, Runnable fence) {
        return load(manifestBytes, analysisId, executionId, fence, true);
    }

    private List<byte[]> load(byte[] manifestBytes, long analysisId, UUID executionId, Runnable fence, boolean reuse) {
        var manifest = contract.parse(manifestBytes, "evidenceManifest");
        // A worker's upload checkpoint alone is not a verified Backend receipt.
        // Reuse only this exact independently verified manifest, then repeat the
        // callback semantic verification with the current frozen verifier.
        boolean verified = reuse && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM analysis_evidence_receipts
                    WHERE analysis_id=? AND execution_id=? AND status='VERIFIED' AND manifest_bytes=?)
                """, Boolean.class, analysisId, executionId, manifestBytes));
        var tasks = new ArrayList<java.util.concurrent.Callable<byte[]>>();
        for (var artifact : manifest.get("artifacts")) {
            fence.run();
            tasks.add(() -> {
                fence.run();
                byte[] raw = verified ? staged(executionId, artifact) : null;
                if (raw == null) raw = reader.readVerified(analysisId, executionId, artifact);
                fence.run();
                return raw;
            });
        }
        try {
            // invokeAll drains the bounded reads before returning; never close a
            // client underneath sibling I/O. Order stays identical to manifest.
            var futures = reads.invokeAll(tasks);
            var originals = new ArrayList<byte[]>();
            for (var future : futures) originals.add(future.get());
            fence.run();
            return originals;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new EvidenceFailure(true);
        } catch (java.util.concurrent.ExecutionException error) {
            if (error.getCause() instanceof EvidenceFailure failure) throw failure;
            if (error.getCause() instanceof org.example.voice.analysis.infrastructure.runpod.RunPodContractException failure) throw failure;
            throw new EvidenceFailure(true);
        }
    }

    private byte[] staged(UUID executionId, com.fasterxml.jackson.databind.JsonNode artifact) {
        var rows = jdbc.queryForList("""
                SELECT staged_bytes,reference_bytes FROM analysis_canonical_upload_journal
                WHERE execution_id=? AND kind=? AND state='VERIFIED' AND sha256=? AND byte_size=?
                """, executionId, artifact.path("kind").asText(), artifact.path("sha256").asText(),
                artifact.path("byteSize").asInt());
        if (rows.isEmpty()) return null;
        var row = rows.getFirst();
        if (!contract.parse((byte[])row.get("reference_bytes"), "journalReference").equals(artifact))
            throw new EvidenceFailure(false);
        byte[] raw = (byte[])row.get("staged_bytes");
        try {
            if (raw == null || raw.length != artifact.path("byteSize").asInt()
                    || !artifact.path("sha256").asText().equals(java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(raw)))) throw new EvidenceFailure(false);
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        return raw;
    }

    @jakarta.annotation.PreDestroy public void close() { reads.shutdownNow(); }
}
