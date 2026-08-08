package org.example.voice.training.exception;

public class RecordingNotFoundException extends RuntimeException {

    public RecordingNotFoundException(String message) {
        super(message);
    }
}
