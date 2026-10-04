package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffDocument;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffStore;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Transport-independent handoff use cases; persistence owns short atomic fences. */
@Service @RequiredArgsConstructor
public class CanonicalHandoffService {
    private final CanonicalHandoffStore store;
    public CanonicalHandoffStore.Snapshot receive(long analysis,UUID worker,CanonicalHandoffDocument doc){
        var id=doc.projection().identity();
        if(analysis!=id.analysisId() || !worker.equals(id.workerId()))throw new RunPodContractException(409,"WORKER_CONFLICT");
        try{return store.receive(analysis,worker,doc);}
        catch(org.springframework.dao.DataIntegrityViolationException error){throw new RunPodContractException(409,"RESULT_EVENT_CONFLICT");}
    }
    public void stage(long analysis,UUID handoff,UUID worker,String kind,byte[] raw){store.stage(analysis,handoff,worker,kind,raw);}
    public CanonicalHandoffStore.Snapshot seal(long analysis,UUID handoff,UUID worker){return store.seal(analysis,handoff,worker);}
    public CanonicalHandoffStore.Snapshot status(long analysis,UUID handoff,UUID worker){return store.status(analysis,handoff,worker);}
}
