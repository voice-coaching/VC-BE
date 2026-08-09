package org.example.voice.analysis.domain.entity;

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
import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_segments")
public class AnalysisSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "expected_text")
    private String expectedText;

    @Column(name = "recognized_text")
    private String recognizedText;

    @Column(name = "start_ms")
    private Integer startMs;

    @Column(name = "end_ms")
    private Integer endMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private SegmentMatchType matchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private SegmentResultStatus resultStatus;

    @Column(name = "target_unit")
    private String targetUnit;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "pronunciation_score")
    private BigDecimal pronunciationScore;

    @Column(name = "intonation_score")
    private BigDecimal intonationScore;

    @Column(name = "feedback")
    private String feedback;
}
