package org.example.voice.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class HttpRequestLogState {
    static final String ATTRIBUTE=HttpRequestLogState.class.getName();
    final String requestId=UUID.randomUUID().toString();
    final long started=System.nanoTime();
    final String method;
    final String route;
    final AtomicBoolean emitted=new AtomicBoolean();
    volatile String errorCode;
    volatile boolean failed;
    HttpRequestLogState(String method,String route){this.method=method;this.route=route;}
    static HttpRequestLogState get(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof HttpRequestLogState state ? state : null;
    }
}
