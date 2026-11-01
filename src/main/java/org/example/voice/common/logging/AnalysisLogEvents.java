package org.example.voice.common.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

/** Observational only: existing signed identities are logged, never changed or republished. */
public final class AnalysisLogEvents {
    private AnalysisLogEvents() {}
    public static LoggingEventBuilder event(Logger log,String event,Long id,String requestId,String executionId,String code) {
        var builder=("analysis_failed".equals(event)?log.atError():code==null?log.atInfo():log.atWarn())
                .addKeyValue("event",event).addKeyValue("analysis_id",id);
        if(uuid(requestId))builder.addKeyValue("analysis_request_id",requestId);
        if(uuid(executionId))builder.addKeyValue("execution_id",executionId);
        if(code!=null)builder.addKeyValue("error_code",code.matches("[A-Z0-9_]{1,80}|(?:analysis|runpod|canonical)_[a-z0-9_]{1,80}")?code:"ANALYSIS_FAILURE");
        return builder;
    }
    public static void afterCommit(Logger log,String event,Long id,String requestId,String executionId,String code) {
        String httpId=MDC.get("request_id");
        Runnable action=()->{
            var previous=MDC.getCopyOfContextMap();
            try {
                if(uuid(httpId))MDC.put("request_id",httpId);else MDC.remove("request_id");
                event(log,event,id,requestId,executionId,code).log("Analysis lifecycle event");
            } catch(RuntimeException ignored) {
                // An appender failure must not fail an already committed business transaction.
            } finally {if(previous==null)MDC.clear();else MDC.setContextMap(previous);}
        };
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){action.run();}
            });
        else action.run();
    }
    private static boolean uuid(String value) {
        if(value==null || !value.matches("[a-fA-F0-9-]{36}"))return false;
        try {UUID.fromString(value);return true;} catch(IllegalArgumentException ignored){return false;}
    }
}
