package org.example.voice.title.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.TitleException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity @Table(name = "title_exams") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleExam {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "previous_rank", nullable = false, length = 30) private TitleRank previousRank;
    @Enumerated(EnumType.STRING) @Column(name = "target_rank", nullable = false, length = 30) private TitleRank targetRank;
    @Column(name = "practice_content_id", nullable = false) private Long practiceContentId;
    private Long practiceExampleSetId;
    private Integer practiceExampleRevision;
    public void pinPracticeExample(Long setId, Integer revision) {
        if (practiceExampleSetId != null || setId == null || revision == null) throw new IllegalStateException("Invalid example pin");
        practiceExampleSetId = setId; practiceExampleRevision = revision;
    }
    @Column(name = "required_training_count", nullable = false) private long requiredTrainingCount;
    @Column(name = "passing_score", nullable = false) private int passingScore;
    @Column(name = "training_session_id", unique = true) private Long trainingSessionId;
    @Column(name = "analysis_id", unique = true) private Long analysisId;
    @Column(nullable = false, length = 20) private String status;
    @Column(precision = 5, scale = 2) private BigDecimal score;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "evaluated_at") private OffsetDateTime evaluatedAt;
    public static TitleExam create(Long userId, TitleRank current, TitlePolicy policy, OffsetDateTime now) {
        var exam = new TitleExam(); exam.userId = userId; exam.previousRank = current;
        exam.targetRank = policy.getTargetRank(); exam.practiceContentId = policy.getPracticeContentId();
        exam.requiredTrainingCount = policy.getRequiredTrainingCount(); exam.passingScore = policy.getPassingScore();
        exam.status = "READY"; exam.createdAt = now.truncatedTo(ChronoUnit.MICROS); return exam;
    }
    public boolean graded() { return status.equals("PASSED") || status.equals("FAILED"); }
    public void bind(Long sessionId) {
        if (graded()) throw new TitleException(409, "TITLE_EXAM_ALREADY_GRADED");
        trainingSessionId = sessionId; status = "IN_PROGRESS";
    }
    public void grade(Long analysisId, BigDecimal score, OffsetDateTime now) {
        if (graded()) throw new TitleException(409, "TITLE_EXAM_ALREADY_GRADED");
        if (trainingSessionId == null || score == null || score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new TitleException(409, "ANALYSIS_NOT_COMPLETED");
        this.analysisId = analysisId; this.score = score;
        status = score.compareTo(BigDecimal.valueOf(passingScore)) >= 0 ? "PASSED" : "FAILED";
        evaluatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
    public TitleRank resultingRank() { return status.equals("PASSED") ? targetRank : previousRank; }
}
