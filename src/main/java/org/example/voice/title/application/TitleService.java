package org.example.voice.title.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.title.domain.*;
import org.example.voice.title.domain.entity.*;
import org.example.voice.title.domain.port.*;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class TitleService implements TitleExamSessionLink {
    private final TitleRepository titles;
    private final UserReader users;
    private final TrainingSessionWriter sessions;
    private final Clock clock;
    private final org.example.voice.practiceexample.domain.port.PracticeExampleReader examples;
    public record Next(String code, String label, long requiredTrainingCount, long remainingTrainingCount, int passingScore, boolean eligible) {}
    public record Progress(String code, String label, long completedTrainingCount, long minimumTrainingCount, Next next, OffsetDateTime updatedAt) {}
    public record Exam(Long id, String currentTitle, String targetTitle, Long practiceContentId,
                       long requiredTrainingCount, int passingScore, String status, OffsetDateTime createdAt, Long trainingSessionId) {}
    public record Grade(Long examId, String status, BigDecimal score, int passingScore, boolean passed,
                        String previousTitle, String currentTitle, OffsetDateTime evaluatedAt) {}

    public Progress progress(Long userId) {
        var user = allowed(users.findById(userId).orElseThrow(() -> new TitleException(404, "RESOURCE_NOT_FOUND")));
        var title = titles.title(userId).orElseGet(() -> UserTitle.initial(userId, user.getCreatedAt()));
        long count = titles.completedCount(userId);
        Next next = null;
        if (title.getRank().next() != null) {
            var rule = policy(title.getRank().next());
            next = new Next(rule.getTargetRank().name(), rule.getTargetRank().label(), rule.getRequiredTrainingCount(),
                    Math.max(0, rule.getRequiredTrainingCount()-count), rule.getPassingScore(), count >= rule.getRequiredTrainingCount());
        }
        return new Progress(title.getRank().name(), title.getRank().label(), count, title.getMinimumTrainingCount(), next, title.getUpdatedAt());
    }

    @Transactional
    public Exam create(Long userId, String key) {
        String digest = digest(key);
        User user = lock(userId);
        if (digest != null) {
            var replay = titles.requested(userId, digest);
            if (replay.isPresent()) return view(owned(replay.get(), userId), true);
        }
        var title = titles.title(userId).orElseGet(() -> titles.save(UserTitle.initial(userId, user.getCreatedAt())));
        var target = title.getRank().next();
        if (target == null) throw new TitleException(409, "MAX_TITLE_REACHED");
        var existing = titles.active(userId, target);
        TitleExam exam;
        if (existing.isPresent()) exam = existing.get();
        else {
            var rule = policy(target);
            if (titles.completedCount(userId) < rule.getRequiredTrainingCount()) throw new TitleException(409, "TITLE_EXAM_NOT_ELIGIBLE");
            if (!titles.availableContent(rule.getPracticeContentId())) throw new TitleException(503, "TITLE_EXAM_CONTENT_UNAVAILABLE");
            exam = TitleExam.create(userId, title.getRank(), rule, now());
            var example = examples.forSession(rule.getPracticeContentId(), null);
            if (example != null) exam.pinPracticeExample(example.setId(), example.revision());
            exam = titles.save(exam);
        }
        if (digest != null) titles.remember(new TitleExamRequest(userId, digest, exam.getId()));
        return view(exam, true);
    }
    public Exam get(Long userId, Long examId) { allowed(users.findById(userId).orElseThrow(() -> new TitleException(404, "RESOURCE_NOT_FOUND"))); return view(owned(examId,userId), false); }

    @Override @Transactional
    public TrainingSessionCreatedData createSession(Long userId, Long examId, Long contentId, Long courseStepId, LearningFocus focus) {
        lock(userId);
        var exam = owned(examId,userId);
        if (exam.graded()) throw new TitleException(409, "TITLE_EXAM_ALREADY_GRADED");
        if (!exam.getPracticeContentId().equals(contentId) || courseStepId != null) throw new TitleException(409, "TITLE_EXAM_CONTENT_MISMATCH");
        var prior = titles.reusableSession(exam.getTrainingSessionId(),userId);
        if (prior.isPresent()) return prior.get();
        var created = sessions.create(userId,contentId,null,focus);
        exam.bind(created.sessionId()); titles.save(exam); return created;
    }

    @Transactional
    public Grade submit(Long userId, Long examId, Long analysisId) {
        lock(userId);
        var exam = owned(examId,userId);
        if (exam.graded()) {
            if (!exam.getAnalysisId().equals(analysisId)) throw new TitleException(409, "TITLE_EXAM_ALREADY_GRADED");
            return grade(exam);
        }
        if (exam.getTrainingSessionId() == null) throw new TitleException(409, "ANALYSIS_NOT_COMPLETED");
        if (!titles.ownedAnalysis(analysisId,userId)) throw new TitleException(404,"RESOURCE_NOT_FOUND");
        var score = titles.score(analysisId, userId, exam.getTrainingSessionId(), exam.getPracticeContentId())
                .orElseThrow(() -> new TitleException(409, "ANALYSIS_NOT_COMPLETED"));
        var title = titles.title(userId).orElseThrow(() -> new TitleException(409, "CONFLICT"));
        if (title.getRank() != exam.getPreviousRank()) throw new TitleException(409, "CONFLICT");
        exam.grade(analysisId,score,now());
        if (exam.getStatus().equals("PASSED")) { title.promote(exam.getTargetRank(),exam.getRequiredTrainingCount(),now()); titles.save(title); }
        titles.save(exam); return grade(exam);
    }
    private Grade grade(TitleExam exam) { return new Grade(exam.getId(), exam.getStatus(), exam.getScore(),exam.getPassingScore(),exam.getStatus().equals("PASSED"),exam.getPreviousRank().label(),exam.resultingRank().label(),exam.getEvaluatedAt()); }
    private Exam view(TitleExam exam, boolean creation) { return new Exam(exam.getId(),exam.getPreviousRank().label(),exam.getTargetRank().label(),exam.getPracticeContentId(),exam.getRequiredTrainingCount(),exam.getPassingScore(),creation ? "READY" : exam.getStatus(),exam.getCreatedAt(),creation ? null : exam.getTrainingSessionId()); }
    private TitleExam owned(Long id, Long userId) { return titles.owned(id,userId).orElseThrow(() -> new TitleException(404,"RESOURCE_NOT_FOUND")); }
    private TitlePolicy policy(TitleRank rank) { return titles.policy(rank).orElseThrow(() -> new TitleException(503,"TEMPORARY_UNAVAILABLE")); }
    private User lock(Long id) { return allowed(users.findByIdForUpdate(id).orElseThrow(() -> new TitleException(404,"RESOURCE_NOT_FOUND"))); }
    private User allowed(User user) { if(user.isWithdrawn() || user.isSuspended()) throw new TitleException(403,"FORBIDDEN"); return user; }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private String digest(String key) {
        if (key == null) return null;
        if (!key.matches("[\\x21-\\x7e]{1,128}")) throw new TitleException(400,"VALIDATION_ERROR");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
