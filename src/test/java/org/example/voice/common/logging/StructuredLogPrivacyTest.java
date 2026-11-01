package org.example.voice.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class StructuredLogPrivacyTest {
    @Test void actualBootEncoderKeepsSearchFieldsAndExcludesPrivateMdcAndExceptionMessages() throws Exception {
        var env=new MockEnvironment().withProperty("logging.structured.json.customizer",SafeLoggingJsonCustomizer.class.getName())
                .withProperty("logging.structured.json.stacktrace.printer",SafeStackTracePrinter.class.getName())
                .withProperty("logging.structured.json.add.service_name","alpha-backend");
        var context=new LoggerContext();context.putObject(Environment.class.getName(),env);
        var encoder=new StructuredLogEncoder();encoder.setContext(context);encoder.setFormat("logstash");encoder.start();
        try {
            var event=new LoggingEvent();event.setLoggerName("safe-test");event.setLevel(Level.ERROR);event.setMessage("Safe diagnostic");
            event.setTimeStamp(System.currentTimeMillis());event.setThreadName("test");
            event.setMDCPropertyMap(Map.of("request_id","25d2f5a9-7f8e-4051-98bd-2b42158cde1f","email","PRIVATE_SENTINEL",
                    "authorization","PRIVATE_SENTINEL","script","PRIVATE_SENTINEL"));
            event.addKeyValuePair(new KeyValuePair("event","api_failed"));
            event.addKeyValuePair(new KeyValuePair("http_status",500));
            event.addKeyValuePair(new KeyValuePair("cookie","PRIVATE_SENTINEL"));
            event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("PRIVATE_SENTINEL",new RuntimeException("PRIVATE_SENTINEL"))));
            String text=new String(encoder.encode(event),StandardCharsets.UTF_8);
            var json=new ObjectMapper().readTree(text);
            assertThat(json.path("event").asText()).describedAs(text).isEqualTo("api_failed");
            assertThat(json.path("http_status").asInt()).isEqualTo(500);
            assertThat(json.path("request_id").asText()).isEqualTo("25d2f5a9-7f8e-4051-98bd-2b42158cde1f");
            assertThat(json.path("service_name").asText()).isEqualTo("alpha-backend");
            assertThat(json.path("stack_trace").asText()).contains("java.lang.IllegalStateException","java.lang.RuntimeException","StructuredLogPrivacyTest");
            assertThat(text).doesNotContain("PRIVATE_SENTINEL","\"email\"","\"authorization\"","\"cookie\"","\"script\"");
            assertThat(text.strip().lines().count()).isEqualTo(1);
        } finally {encoder.stop();context.stop();}
    }
}
