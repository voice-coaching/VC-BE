package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument;
import org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackInbox;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.stereotype.Service;

/** PENDING must commit before the retryable HTTP 503; no encompassing transaction here. */
@Service
@RequiredArgsConstructor
public class CanonicalCallbackService {
    private final CanonicalCallbackInbox inbox;
    private final CanonicalCallbackCommitter committer;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalBackendJournal journal;
    private final org.example.voice.analysis.infrastructure.runpod.RunPodContract contract;
    @org.springframework.transaction.annotation.Transactional(timeout=5)
    public org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackResponseDto recoverAcknowledgement(
            long analysisId,java.util.UUID execution,java.util.UUID worker) {
        var doc=CanonicalCallbackDocument.parse(journal.storedCallback(analysisId,execution,worker),contract);
        inbox.confirmApplied(doc);
        return journal.acknowledgement(doc,"DUPLICATE");
    }
    @org.springframework.transaction.annotation.Transactional(timeout=5)
    public org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackResponseDto acknowledgement(
            long analysisId,CanonicalCallbackDocument document,String disposition) {
        if(document.identity().analysisId()!=analysisId)throw new RunPodContractException(422,"VALIDATION_FAILED");
        inbox.confirmApplied(document);
        return journal.acknowledgement(document,disposition);
    }
    public void confirmApplied(long analysisId,CanonicalCallbackDocument document) {
        if(document.identity().analysisId()!=analysisId)throw new RunPodContractException(422,"VALIDATION_FAILED");
        inbox.confirmApplied(document);
    }
    public AnalysisResultIngestionDisposition ingest(long analysisId,CanonicalCallbackDocument document) {
        if(document.identity().analysisId()!=analysisId)throw new RunPodContractException(422,"VALIDATION_FAILED");
        return switch(inbox.register(document)) {
            case APPLIED -> AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
            case VERIFIED -> committer.commit(document);
            case PENDING -> throw new RunPodContractException(503,"DEPENDENCY_UNAVAILABLE");
        };
    }
}
