package org.example.voice.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

class AnalysisLogEventsTest {
    @Test void lifecycleSuccessIsPublishedAfterCommitAndSuppressedOnRollback() {
        var log=(Logger)LoggerFactory.getLogger(AnalysisLogEventsTest.class);
        var logs=new ListAppender<ILoggingEvent>();logs.start();log.addAppender(logs);
        try {
            TransactionSynchronizationManager.initSynchronization();
            AnalysisLogEvents.afterCommit(log,"analysis_completed",12L,null,null,null);
            assertThat(logs.list).isEmpty();
            var committed=TransactionSynchronizationManager.getSynchronizations();
            committed.forEach(TransactionSynchronization::afterCommit);
            assertThat(logs.list).hasSize(1);
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            AnalysisLogEvents.afterCommit(log,"analysis_queued",13L,null,null,null);
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(logs.list).hasSize(1);
        } finally {if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();log.detachAppender(logs);logs.stop();}
    }
}
