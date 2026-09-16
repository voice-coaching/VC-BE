package org.example.voice.practiceexample.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Table(name = "example_audio_cache")
public class ExampleAudioEntry {
    @Id @Column(length = 64) private String id;
    @Column(length = 36) private String lease;
    private OffsetDateTime leaseUntil;
    @Column(columnDefinition = "bytea") private byte[] audio;
    @Column(length = 66) private String etag;
    public ExampleAudioEntry(String id) { this.id = id; }
    public void claim(String lease, OffsetDateTime until) { this.lease = lease; this.leaseUntil = until; }
}
