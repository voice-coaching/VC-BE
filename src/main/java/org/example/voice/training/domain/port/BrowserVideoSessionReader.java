package org.example.voice.training.domain.port;

public interface BrowserVideoSessionReader {
    record Source(Long contentId, String scriptText) {}
    Source ownedSource(Long sessionId, Long userId);
}
