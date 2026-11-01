package org.example.voice.title.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.title.domain.TitleException;
import org.example.voice.title.domain.port.TitleRepository;
import org.example.voice.training.application.TrainingSessionService;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Explicit user restart; merely opening or resuming an exam never calls this. */
@Service
@RequiredArgsConstructor
public class TitleExamRestartService {
    private final TitleRepository titles;
    private final UserReader users;
    private final TrainingSessionWriter writer;
    private final TrainingSessionService sessions;
    private final TitleService service;

    @Transactional
    public TitleService.Exam restart(Long userId, Long examId, Long previousSessionId) {
        // Same user -> session lock order as exam creation and grading.
        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new TitleException(404, "RESOURCE_NOT_FOUND"));
        if (user.isWithdrawn() || user.isSuspended()) throw new TitleException(403, "FORBIDDEN");
        var exam = titles.owned(examId, userId)
                .orElseThrow(() -> new TitleException(404, "RESOURCE_NOT_FOUND"));
        if (exam.graded()) throw new TitleException(409, "TITLE_EXAM_ALREADY_GRADED");
        if (previousSessionId == null || previousSessionId <= 0)
            throw new TitleException(400, "VALIDATION_ERROR");
        // Lost acknowledgements and a second tab must not cancel the replacement.
        if (!previousSessionId.equals(exam.getTrainingSessionId())) return service.get(userId, examId);
        if (!titles.availableContent(exam.getPracticeContentId()))
            throw new TitleException(503, "TITLE_EXAM_CONTENT_UNAVAILABLE");

        writer.lockAnalysisSelection(previousSessionId, userId);
        var status = sessions.getSession(previousSessionId, userId).status();
        if (!Set.of(TrainingSessionStatus.COMPLETED, TrainingSessionStatus.FAILED,
                TrainingSessionStatus.CANCELED).contains(status)) {
            // Includes cancellation outbox, recording cleanup, consent revocation
            // and upload-intent expiry, with the existing execution fences.
            sessions.cancel(previousSessionId, userId);
        }
        // Completed attempts remain history. Their scores cannot grade this new link.
        var replacement = writer.create(userId, exam.getPracticeContentId(), null, LearningFocus.PRONUNCIATION);
        exam.bind(replacement.sessionId());
        titles.save(exam);
        return service.get(userId, examId);
    }
}
