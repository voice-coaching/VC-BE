package org.example.voice.practicecontent.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.practicecontent.domain.type.SpeakerType;

import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reference_audios")
public class ReferenceAudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private PracticeContent content;

    @Column(name = "speaker_name")
    private String speakerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker_type")
    private SpeakerType speakerType;

    @Column(name = "audio_url", nullable = false)
    private String audioUrl;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "is_primary", nullable = false)
    private Boolean primary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
