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

        if (canonicalVideoSha256 == null) {

        } else {}
        for (var base : new String[]{canonicalAudioTimeBase, canonicalVideoTimeBase}) {

        }
        for (var seconds : new BigDecimal[]{canonicalAudioStartSeconds, canonicalVideoStartSeconds}) {

        }
    }

    public boolean binds(String audio, String video) {
        return canonicalPcmSha256.equals(audio) && java.util.Objects.equals(canonicalVideoSha256, video);
    }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

}
