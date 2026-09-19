package org.example.voice.practicecontent.domain.port;

public interface CustomContentLifecycle {
    boolean validateSession(Long contentId,Long userId,Long courseStepId,Long titleExamId);
    Long prepareHistoryDeletion(Long sessionId,Long userId);
    void afterHistoryDeletion(Long contentId,Long userId);
    void eraseForUser(Long userId);
}
