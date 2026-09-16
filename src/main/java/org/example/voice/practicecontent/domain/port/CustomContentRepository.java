package org.example.voice.practicecontent.domain.port;

import java.util.Optional;
import org.example.voice.practicecontent.domain.entity.*;

public interface CustomContentRepository {
    Optional<Long> owner(Long contentId);
    Optional<PracticeContent> owned(Long contentId,Long userId);
    Optional<CustomContentRequest> request(Long userId,String keyDigest);
    PracticeContent save(PracticeContent content);
    void remember(CustomContentRequest request);
    void eraseIfUnreferenced(Long contentId,Long userId);
    void eraseForUser(Long userId);
    Long sessionContent(Long sessionId,Long userId);
}
