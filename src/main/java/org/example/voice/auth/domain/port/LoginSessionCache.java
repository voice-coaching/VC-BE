package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.model.LoginSessionState;
import java.util.Optional;

public interface LoginSessionCache {
    default boolean enabled() { return true; }
    enum Kind { CACHED, LOAD, BYPASS }
    record Lookup(Kind kind, LoginSessionState session, String ticket) {
        public static Lookup bypass() { return new Lookup(Kind.BYPASS, null, null); }
        @Override public String toString() { return "SessionCacheLookup[" + kind + "]"; }
    }
    Lookup lookup(Long userId);
    void publish(Long userId, String ticket, Optional<LoginSessionState> session);
    String beginChange(Long userId);
    void finishChange(Long userId, String changeId);
}
