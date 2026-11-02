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
    @org.springframework.beans.factory.annotation.Autowired
    private org.example.voice.practiceexample.domain.port.ExampleTtsGenerator directGenerator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Audio audio(Long userId, String exampleId, String voice) {
        Snapshot example = examples.audioSource(userId, exampleId);
        if (tts.runpod()) {
            var available=generated.playable(exampleId,tts.getRevision());
            if(available.isPresent())return available.get();
            var job=new org.example.voice.practiceexample.domain.model.ExampleTtsData.Job(
                    0,example.id(),example.text(),example.revision(),tts.getRevision(),java.util.UUID.randomUUID(),1);
            var audio=directGenerator.generate(job);
            return new Audio(audio.bytes(),'"'+audio.sha256()+'"');
        }
        if (voice == null) voice = DEFAULT_VOICE;

        String key = hash((example.id() + "|" + example.revision() + "|" + voice + "|0.92").getBytes(StandardCharsets.UTF_8));
        var cached = cache.cached(key);
        if (cached.isPresent()) return cached.get();
        Claim claim = cache.claim(key, userId, OffsetDateTime.now(clock));
        if (claim.cached() != null) return claim.cached();
        try {
            byte[] bytes = synthesizer.synthesize(example.text(), voice, RATE);

            Audio audio = new Audio(bytes, '"' + hash(bytes) + '"');
            cache.complete(key, claim.lease(), audio); return audio;
        } catch (RuntimeException e) {
            try { cache.fail(key, claim.lease(), OffsetDateTime.now(clock)); } catch (RuntimeException ignored) { /* Lease still expires. */ }

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
