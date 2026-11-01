package org.example.voice.common.logging;

import org.example.voice.common.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class HttpErrorLogAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter type,Class<? extends HttpMessageConverter<?>> converter){return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter type,MediaType media,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(body instanceof ApiResponse<?> result && request instanceof ServletServerHttpRequest servlet)
            HttpLogErrors.record(servlet.getServletRequest(),result.getCode());
        return body;
    }
}
