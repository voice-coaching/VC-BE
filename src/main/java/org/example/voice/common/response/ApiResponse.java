package org.example.voice.common.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"result", "message", "data", "code"})
public class ApiResponse<T> {

    private final boolean result;
    private final String message;
    private final T data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String code;

    private ApiResponse(boolean result, String message, T data) {
        this(result, message, data, null);
    }

    private ApiResponse(boolean result, String message, T data, String code) {
        this.result = result;
        this.message = message;
        this.data = data;
        this.code = code;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public static ApiResponse<Void> error(String message, String code) {
        return new ApiResponse<>(false, message, null, code);
    }
}
