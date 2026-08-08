package org.example.voice.common.response;

public record ErrorResponse(
        boolean result,
        String message,
        Object data
) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(false, message, null);
    }
}
