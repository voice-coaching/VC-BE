package org.example.voice.common.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

public final class HttpRequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER="X-Request-ID";
    private static final Logger LOG=LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private final HttpLogRouteResolver routes;
    public HttpRequestLoggingFilter(HttpLogRouteResolver routes){this.routes=routes;}
    @Override protected boolean shouldNotFilterAsyncDispatch(){return false;}
    @Override protected boolean shouldNotFilterErrorDispatch(){return false;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpRequestLogState.get(request)==null && !request.getRequestURI().startsWith("/api/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var state=HttpRequestLogState.get(request);
        if(state==null) {state=new HttpRequestLogState(safeMethod(request.getMethod()),routes.resolve(request));request.setAttribute(HttpRequestLogState.ATTRIBUTE,state);}
        response.setHeader(REQUEST_ID_HEADER,state.requestId);
        var previous=MDC.getCopyOfContextMap();
        bind(state);
        try {chain.doFilter(request,response);}
        catch(IOException|ServletException|RuntimeException|Error error){state.failed=true;throw error;}
        finally {
            try {
                if(request.isAsyncStarted()) listen(request,response,state);
                else emit(response,state);
            } finally {restore(previous);}
        }
    }
    private void listen(HttpServletRequest request,HttpServletResponse response,HttpRequestLogState state) {
        var listener=new AsyncListener() {
            @Override public void onComplete(AsyncEvent event){emit(response,state);}
            @Override public void onTimeout(AsyncEvent event){state.failed=true;}
            @Override public void onError(AsyncEvent event){state.failed=true;}
            @Override public void onStartAsync(AsyncEvent event){event.getAsyncContext().addListener(this);}
        };
        try {request.getAsyncContext().addListener(listener);}
        catch(IllegalStateException finished){emit(response,state);}
    }
    private void emit(HttpServletResponse response,HttpRequestLogState state) {
        if(!state.emitted.compareAndSet(false,true))return;
        var previous=MDC.getCopyOfContextMap();
        try {
            bind(state);
            int status=response.getStatus();
            if(state.failed && status<400)status=500;
            var event=(status>=500?LOG.atError():LOG.atInfo()).addKeyValue("event","http_request_completed")
                    .addKeyValue("http_method",state.method).addKeyValue("route",state.route)
                    .addKeyValue("http_status",status).addKeyValue("duration_ms",Math.max(0,(System.nanoTime()-state.started)/1_000_000));
            if(state.errorCode!=null)event.addKeyValue("error_code",state.errorCode);
            else if(status>=400)event.addKeyValue("error_code","HTTP_"+status);
            event.log("HTTP request completed");
        } catch(RuntimeException ignored) {
            // Observability must not replace a business response or fail an async callback.
        } finally {restore(previous);}
    }
    private static String safeMethod(String value) {
        return switch(value){case "GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE"->value;default->"OTHER";};
    }
    private static void bind(HttpRequestLogState state){MDC.put("request_id",state.requestId);}
    private static void restore(Map<String,String> previous){if(previous==null)MDC.clear();else MDC.setContextMap(previous);}
}
