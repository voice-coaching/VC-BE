package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class RunPodDueRowPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisRequestOutboxJpaRepository outboxes;
    @Test void olderFutureRetryDoesNotBlockTwoReadyRowsAndReservationAdvancesSelection(){
        long analysis=-179004L,waiting=-179003L,ready=-179002L,next=-179001L;
        jdbc.update("INSERT INTO analysis_results(id,status,created_at) VALUES(?,'PENDING',CURRENT_TIMESTAMP)",analysis);
        for(long id:new long[]{waiting,ready,next}){
            jdbc.update("""
                INSERT INTO analysis_request_outbox(id,event_id,analysis_id,payload,status,attempt_count,next_attempt_at,created_at,transport,execution_id)
                VALUES(?,?,?,'{}','PENDING',0,?,CURRENT_TIMESTAMP,'RUNPOD_HTTP',?)
                """,id,UUID.randomUUID().toString(),analysis,
                id==waiting?OffsetDateTime.now().plusMinutes(5):OffsetDateTime.now().minusMinutes(1),UUID.randomUUID().toString());
        }
        var first=outboxes.findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc("RUNPOD_HTTP",AnalysisRequestOutboxStatus.PENDING,OffsetDateTime.now()).orElseThrow();
        assertThat(first.getId()).isEqualTo(ready);
        first.reserveDeliveryUntil(OffsetDateTime.now().plusSeconds(60));outboxes.flush();
        var second=outboxes.findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc("RUNPOD_HTTP",AnalysisRequestOutboxStatus.PENDING,OffsetDateTime.now()).orElseThrow();
        assertThat(second.getId()).isEqualTo(next);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM analysis_request_outbox WHERE id=?",Integer.class,waiting)).isZero();
    }
}
