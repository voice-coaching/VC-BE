package org.example.voice.training.domain.model;

import java.math.BigDecimal;

/** Server-produced media identity. A probed stream start is not a verified sync map. */
public record MediaPreparationData(
        String schemaVersion, String sourceSha256, String canonicalPcmSha256,
        String canonicalVideoSha256, String normalizationRevision,
        BigDecimal canonicalAudioStartSeconds, BigDecimal canonicalVideoStartSeconds,
        String canonicalAudioTimeBase, String canonicalVideoTimeBase, String syncStatus) {
    public static final String SCHEMA = "voice-coaching.media-preparation.v1";
    public static final String REVISION = "backend-mp4-pcm-20261007-v1";

    public MediaPreparationData {
        if (!SCHEMA.equals(schemaVersion) || !REVISION.equals(normalizationRevision)
                || !sha(sourceSha256) || !sha(canonicalPcmSha256)
                || (canonicalVideoSha256 != null && !sha(canonicalVideoSha256))) invalid();
        if (canonicalVideoSha256 == null) {
            if (!"NOT_APPLICABLE".equals(syncStatus) || canonicalVideoStartSeconds != null
                    || canonicalVideoTimeBase != null) invalid();
        } else if (!"UNVERIFIED".equals(syncStatus)) invalid();
        for (var base : new String[]{canonicalAudioTimeBase, canonicalVideoTimeBase}) {
            if (base != null && !base.matches("[1-9][0-9]{0,9}/[1-9][0-9]{0,9}")) invalid();
        }
        for (var seconds : new BigDecimal[]{canonicalAudioStartSeconds, canonicalVideoStartSeconds}) {
            if (seconds != null && (seconds.abs().compareTo(BigDecimal.valueOf(86400)) > 0
                    || seconds.scale() > 12)) invalid();
        }
    }

    public boolean binds(String audio, String video) {
        return canonicalPcmSha256.equals(audio) && java.util.Objects.equals(canonicalVideoSha256, video);
    }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static void invalid() { throw new IllegalArgumentException("MEDIA_PREPARATION_INVALID"); }
}
