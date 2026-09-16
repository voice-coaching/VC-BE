package org.example.voice.practiceexample.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.voice.practiceexample.domain.PracticeExampleException;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Table(name = "example_tts_quotas")
public class ExampleTtsQuota {
    @Id private Long id;
    @Column(nullable = false) private long windowMinute;
    @Column(nullable = false) private int attempts;
    public ExampleTtsQuota(Long id) { this.id = id; }
    public void consume(long minute, int limit) {
        if (minute != windowMinute) { windowMinute = minute; attempts = 0; }
        if (attempts >= limit) throw new PracticeExampleException(429, "TTS_RATE_LIMITED");
        attempts++;
    }
}
