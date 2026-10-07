package org.example.voice.analysis.infrastructure.canonical;

import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Fixed stage labels and identity only; never log evidence or exception text. */
final class CanonicalTiming {
    static <T> T measure(long analysisId, UUID executionId, String stage, Supplier<T> operation) {
        long started = System.nanoTime();
        String code = "FAILED";
        try {
            T result = operation.get();
            code = "OK";
            return result;
        } finally {
            LoggerFactory.getLogger(CanonicalTiming.class).info(
                    "canonical_span utc={} analysisId={} executionId={} stage={} elapsedMs={} code={}",
                    Instant.now(), analysisId, executionId, stage, (System.nanoTime()-started)/1_000_000, code);
        }
    }
}
