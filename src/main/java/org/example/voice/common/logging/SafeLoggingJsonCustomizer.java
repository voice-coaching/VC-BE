package org.example.voice.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import java.util.Set;

/** Keep arbitrary MDC/SLF4J values out of operational logs. No user identities or payload fields. */
public final class SafeLoggingJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    private static final Set<String> FIELDS=Set.of("@timestamp","@version","message","logger_name","thread_name",
            "level","level_value","service_name","event","http_method","route","http_status","duration_ms",
            "request_id","error_code","exception_type","stack_trace","analysis_id","analysis_request_id",
            "execution_id","queue_age_ms","contract_kind","violation_count","field_paths");
    @Override public void customize(JsonWriter.Members<ILoggingEvent> members) {
        // JsonWriter's predicate identifies excluded paths (true means omit), not allowed paths.
        members.applyingPathFilter(path -> !path.name().isEmpty() && !FIELDS.contains(path.name()));
    }
}
