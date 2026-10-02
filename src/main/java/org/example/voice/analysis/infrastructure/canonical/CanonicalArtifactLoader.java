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

    CanonicalArtifactLoader(RunPodContract contract, CanonicalEvidenceReader reader) {
        this.contract = contract;
        this.reader = reader;
    }

    List<byte[]> load(byte[] manifestBytes, long analysisId, UUID executionId, Runnable fence) {
        var manifest = contract.parse(manifestBytes, "evidenceManifest");
        var originals = new ArrayList<byte[]>();
        for (var artifact : manifest.get("artifacts")) {
            fence.run();
            originals.add(reader.readVerified(analysisId, executionId, artifact));
        }
        return originals;
    }
}
