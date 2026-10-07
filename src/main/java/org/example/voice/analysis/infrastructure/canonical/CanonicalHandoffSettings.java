package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;

@Component @RequiredArgsConstructor
public class CanonicalHandoffSettings {
    private final Environment environment;
    private final JdbcTemplate jdbc;
    public static final long RESERVATION=85_065_728L;
    public static final long AUDIOVISUAL_RESERVATION=118_620_160L;
    public boolean workerEnabled(){return "true".equals(value("worker-enabled"));}
    public boolean admissionEnabled(){return "true".equals(value("admission-enabled"));}
    public boolean archiveEnabled(){return "true".equals(value("archive-enabled"));}
    public String value(String name){return environment.getProperty("analysis.canonical.handoff."+name,"");}
    public long budget(){try{long n=Long.parseLong(value("spool-budget-bytes"));return n>=RESERVATION && n<=16L*1024*1024*1024?n:0;}catch(NumberFormatException e){return 0;}}
    public long used(){return jdbc.queryForObject("""
        SELECT COALESCE((SELECT SUM(reserved_bytes) FROM analysis_canonical_handoffs),0) +
        (SELECT COALESCE(SUM(CASE WHEN e.result_schema_version='voice-coaching.runpod-analysis-result.v6'
          THEN 118620160 ELSE 85065728 END),0)
        FROM analysis_canonical_executions e JOIN analysis_results a ON a.active_execution_id=e.execution_id::text
        WHERE e.result_schema_version IN ('voice-coaching.runpod-analysis-result.v5','voice-coaching.runpod-analysis-result.v6')
        AND a.status IN ('PENDING','PROCESSING')
        AND NOT EXISTS(SELECT 1 FROM analysis_canonical_handoffs h WHERE h.execution_id=e.execution_id))
        """,Long.class);}
    /** Called within the admission/receive transaction, shared across all Backend instances. */
    public void reserveAdmissionBudget(long additional) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("TRANSACTION_REQUIRED");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(193589719,39)");
        if(budget()==0 || used()>budget()-additional)throw new RunPodContractException(429,"CAPACITY_EXCEEDED");
    }
}
