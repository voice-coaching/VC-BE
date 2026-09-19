package org.example.voice.practiceexample.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.model.ExampleData.*;
import org.example.voice.practiceexample.domain.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service @RequiredArgsConstructor
public class ExampleAudioService {
    public static final String DEFAULT_VOICE = "ko-KR-Chirp3-HD-Aoede";
    public static final double RATE = 0.92;
    private final PracticeExampleService examples;
    private final ExampleAudioCache cache;
    private final ExampleSpeechSynthesizer synthesizer;
    private final Clock clock;
    private final ExampleTtsProperties tts;
    private final ExampleTtsStore generated;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Audio audio(Long userId, String exampleId, String voice) {
        Snapshot example = examples.audioSource(userId, exampleId);
        if (tts.runpod()) {
            if (voice != null && !tts.getVoice().equals(voice)) throw PracticeExampleException.invalid();
            try { return generated.playable(exampleId, tts.getRevision()).orElseThrow(PracticeExampleException::unavailable); }
            catch (PracticeExampleException e) { throw e; }
            catch (RuntimeException e) { throw PracticeExampleException.unavailable(); }
        }
        if ("disabled".equals(tts.getProvider())) throw PracticeExampleException.unavailable();
        if (voice == null) voice = DEFAULT_VOICE;
        if (!DEFAULT_VOICE.equals(voice)) throw PracticeExampleException.invalid();
        String key = hash((example.id() + "|" + example.revision() + "|" + voice + "|0.92").getBytes(StandardCharsets.UTF_8));
        var cached = cache.cached(key);
        if (cached.isPresent()) return cached.get();
        Claim claim = cache.claim(key, userId, OffsetDateTime.now(clock));
        if (claim.cached() != null) return claim.cached();
        try {
            byte[] bytes = synthesizer.synthesize(example.text(), voice, RATE);
            if (bytes == null || bytes.length < 3 || bytes.length > 2_000_000 || !mp3(bytes)) throw PracticeExampleException.unavailable();
            Audio audio = new Audio(bytes, '"' + hash(bytes) + '"');
            cache.complete(key, claim.lease(), audio); return audio;
        } catch (RuntimeException e) {
            try { cache.fail(key, claim.lease(), OffsetDateTime.now(clock)); } catch (RuntimeException ignored) { /* Lease still expires. */ }
            if (e instanceof PracticeExampleException error) throw error;
            throw PracticeExampleException.unavailable();
        }
    }
    private boolean mp3(byte[] bytes) {
        return (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') || ((bytes[0] & 255) == 255 && (bytes[1] & 224) == 224);
    }
    private String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
