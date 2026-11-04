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
        var exam=titles.owned(examId,userId).orElseThrow(() -> new TitleException(404,"RESOURCE_NOT_FOUND"));
        Long selectedUser=userId == null ? exam.getUserId() : userId;
        Long current=exam.getTrainingSessionId();
        if(current!=null) sessions.cancel(current,selectedUser);
        var replacement=writer.create(selectedUser,exam.getPracticeContentId(),null,LearningFocus.PRONUNCIATION);
        exam.bind(replacement.sessionId());titles.save(exam);
        return service.get(selectedUser,examId);
    }
}
