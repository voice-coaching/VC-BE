package org.example.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceApplication.class, args);
    }

}
