package org.example.voice.title.controller;

import org.example.voice.title.application.TitleService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class TitleDtos {
    private TitleDtos() {}
    public record Next(String code, String label, long requiredTrainingCount, long remainingTrainingCount, int passingScore, boolean eligible) {
        static Next from(TitleService.Next n) { return n == null ? null : new Next(n.code(),n.label(),n.requiredTrainingCount(),n.remainingTrainingCount(),n.passingScore(),n.eligible()); }
    }
    public record Progress(String code, String label, long completedTrainingCount, long minimumTrainingCount, Next next, OffsetDateTime updatedAt) {
        static Progress from(TitleService.Progress p) {return new Progress(p.code(),p.label(),p.completedTrainingCount(),p.minimumTrainingCount(),Next.from(p.next()),p.updatedAt());}
    }
    public record Exam(Long id,String currentTitle,String targetTitle,Long practiceContentId,long requiredTrainingCount,
                       int passingScore,String status,OffsetDateTime createdAt,Long trainingSessionId) {
        static Exam from(TitleService.Exam e) {return new Exam(e.id(),e.currentTitle(),e.targetTitle(),e.practiceContentId(),e.requiredTrainingCount(),e.passingScore(),e.status(),e.createdAt(),e.trainingSessionId());}
    }
    public record Grade(Long examId,String status,BigDecimal score,int passingScore,boolean passed,String previousTitle,String currentTitle,OffsetDateTime evaluatedAt) {
        static Grade from(TitleService.Grade g) {return new Grade(g.examId(),g.status(),g.score(),g.passingScore(),g.passed(),g.previousTitle(),g.currentTitle(),g.evaluatedAt());}
    }
}
