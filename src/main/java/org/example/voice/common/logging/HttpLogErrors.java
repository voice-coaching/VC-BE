package org.example.voice.common.logging;

import jakarta.servlet.http.HttpServletRequest;

/** Only machine-readable codes enter request logs; response bodies are never buffered or inspected. */
public final class HttpLogErrors {
    private HttpLogErrors() {}
    public static void record(HttpServletRequest request,String code) {
        var state=HttpRequestLogState.get(request);
        if(state!=null && code!=null && code.matches("[A-Z][A-Z0-9_]{0,95}"))state.errorCode=code;
    }
}
