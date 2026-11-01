package org.example.voice.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class HttpRequestLoggingFilterTest {
    final Logger logger=(Logger)LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    final ListAppender<ILoggingEvent> logs=new ListAppender<>() {
        @Override protected void append(ILoggingEvent event){event.prepareForDeferredProcessing();super.append(event);}
    };
    HttpRequestLoggingFilter filter;
    @BeforeEach void setup() throws Exception {
        var mappings=new RequestMappingHandlerMapping();
        mappings.registerMapping(RequestMappingInfo.paths("/api/probe/{id}").methods(RequestMethod.GET).build(),this,getClass().getDeclaredMethod("handler"));
        filter=new HttpRequestLoggingFilter(new HttpLogRouteResolver(mappings));logs.start();logger.addAppender(logs);
    }
    @AfterEach void cleanup(){logger.detachAppender(logs);logs.stop();MDC.clear();}
    void handler() {}
    Map<String,Object> fields(){return logs.list.getLast().getKeyValuePairs().stream().collect(Collectors.toMap(p->p.key,p->p.value));}
    @Test void templatesAndServerIdsPreventRawUrlQueryHeaderAndBodyLoggingAndRestoreMdc() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/probe/PRIVATE_SENTINEL");
        request.setQueryString("token=PRIVATE_SENTINEL");request.addHeader("Authorization","Bearer PRIVATE_SENTINEL");
        request.addHeader("X-Request-ID","PRIVATE_SENTINEL");request.setContent("PRIVATE_SENTINEL".getBytes());
        var response=new MockHttpServletResponse();MDC.put("outer","preserved");
        filter.doFilter(request,response,(r,s)->{
            assertThat(MDC.get("request_id")).isEqualTo(response.getHeader("X-Request-ID"));
            response.setStatus(403);HttpLogErrors.record(request,"COACHING_QUOTA_EXCEEDED");
        });
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
        assertThat(logs.list).hasSize(1);
        assertThat(fields()).containsEntry("route","/api/probe/{id}").containsEntry("http_status",403)
                .containsEntry("error_code","COACHING_QUOTA_EXCEEDED");
        assertThat(fields().toString()+logs.list.getFirst().getFormattedMessage()).doesNotContain("PRIVATE_SENTINEL");
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("outer","preserved"));
    }
    @Test void unknownRoutesDoNotExposeAnArbitraryPath() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/unknown/PRIVATE_SENTINEL");
        filter.doFilter(request,new MockHttpServletResponse(),(r,s)->((MockHttpServletResponse)s).setStatus(404));
        assertThat(fields()).containsEntry("route","<unmapped>").containsEntry("error_code","HTTP_404");
        assertThat(MDC.get("request_id")).isNull();
    }
    @Test void asyncCompletionLogsOnceAndDoesNotLeaveMdcOnEitherThread() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/probe/1");request.setAsyncSupported(true);
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(r,s)->r.startAsync(r,s));
        assertThat(logs.list).isEmpty();assertThat(MDC.get("request_id")).isNull();
        var async=(MockAsyncContext)request.getAsyncContext();
        for(var listener:async.getListeners()) {listener.onComplete(new AsyncEvent(async));listener.onComplete(new AsyncEvent(async));}
        assertThat(logs.list).hasSize(1);assertThat(fields()).containsEntry("http_status",200);
        assertThat(MDC.get("request_id")).isNull();
    }
    @Test void originalExceptionSurvivesAndAnUncommittedStatusIsLoggedAs500() {
        var request=new MockHttpServletRequest("GET","/api/probe/1");var response=new MockHttpServletResponse();
        var error=new ServletException("PRIVATE_SENTINEL");
        assertThatThrownBy(()->filter.doFilter(request,response,(r,s)->{throw error;})).isSameAs(error);
        assertThat(fields()).containsEntry("http_status",500);
        assertThat(logs.list.getFirst().getFormattedMessage()).doesNotContain("PRIVATE_SENTINEL");
        assertThat(MDC.get("request_id")).isNull();
    }
}
