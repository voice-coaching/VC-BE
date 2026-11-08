package org.example.voice.training.domain.port;

import java.util.Map;

public interface BrowserVideoGrantEncoder {
    String TYPE = "voice-coaching.browser-video-grant.v1";
    String sign(Map<String,Object> claims);
}
