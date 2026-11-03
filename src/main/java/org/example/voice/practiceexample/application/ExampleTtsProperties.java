package org.example.voice.practiceexample.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("example.tts")
public class ExampleTtsProperties {
    private String provider = "google";
    private boolean workerEnabled = true;
    private String baseUrl = "";
    private String apiToken = "";
    private String revision = "";
    private String fingerprint = "";
    private String voice = "ko-KR-practice-v1";
    private double speakingRate = 0.92;
    private String decoder = "/usr/bin/ffmpeg";
    private String probe = "/usr/bin/ffprobe";
    public boolean runpod() { return "runpod".equals(provider); }
    public boolean configured() { return true; }
}
