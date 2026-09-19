package org.example.voice.practiceexample.domain.port;

public interface ExampleSpeechSynthesizer {
    byte[] synthesize(String text, String voice, double speakingRate);
}
