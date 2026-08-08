package org.example.voice.training.exception;

public class TrainingSessionNotFoundException extends RuntimeException {

    public TrainingSessionNotFoundException(String message) {
        super(message);
    }
}
