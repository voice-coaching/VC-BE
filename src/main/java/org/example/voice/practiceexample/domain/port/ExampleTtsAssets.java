package org.example.voice.practiceexample.domain.port;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.*;
import java.time.OffsetDateTime;
public interface ExampleTtsAssets {
    void store(Job job, Generated audio);
    Playback playback(String key);
    record Playback(String url, OffsetDateTime expiresAt) {}
    static String key(Job job, Generated audio) { return "tts/practice/"+job.id()+"/"+audio.sha256()+".mp3"; }
}
