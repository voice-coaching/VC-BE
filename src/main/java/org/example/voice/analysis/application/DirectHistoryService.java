package org.example.voice.analysis.application;

import org.example.voice.analysis.infrastructure.DirectHistoryRepository;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class DirectHistoryService {
    private final DirectHistoryRepository repository;
    private final ObjectMapper mapper;
    public DirectHistoryService(DirectHistoryRepository repository, ObjectMapper mapper) {
        this.repository=repository; this.mapper=mapper;
    }
    @Transactional
    public void receive(JsonNode doc) {
        require("voice-coaching.analysis-history-event.v1".equals(doc.path("schemaVersion").asText()));
        var job=uuid(doc.path("jobId").asText()); var execution=uuid(doc.path("executionId").asText());
        var claim=doc.path("historyClaimSha256").asText(); var digest=doc.path("resultSha256").asText();
        require(claim.matches("[a-f0-9]{64}") && digest.matches("[a-f0-9]{64}"));
        var result=doc.path("result");
        require(result.isObject() && result.path("score").isObject() && result.path("decision").isObject());
        require(execution.toString().equals(result.path("sourceIdentity").path("executionId").asText()));
        require(doc.path("audioSha256").asText().matches("[a-f0-9]{64}")
            && doc.path("audioSha256").asText().equals(result.path("sourceIdentity").path("audioSha256").asText()));
        String script=doc.path("scriptText").asText(); require(!script.isBlank() && script.length()<=8000);
        require(sha(script).equals(doc.path("scriptSha256").asText())
            && sha(script).equals(result.path("sourceIdentity").path("scriptSha256").asText()));
        Long content=doc.path("contentId").isNumber()?doc.path("contentId").asLong():null;
        String archive=doc.path("archive").isObject()?doc.path("archive").toString():null;
        if(!repository.receive(job,execution,claim,digest,result.toString(),script,content,archive))
            throw new RunPodContractException(409,"DIRECT_HISTORY_CONFLICT");
    }
    @Transactional
    public String link(long user, UUID job, String claim) {
        require(claim!=null && claim.matches("[A-Za-z0-9_-]{43}"));
        if(!repository.link(user,job,sha(claim)))throw new RunPodContractException(409,"DIRECT_HISTORY_CONFLICT");
        return repository.state(user,job);
    }
    public String state(long user, UUID job) {return repository.state(user,job);}
    public List<Map<String,Object>> list(long user) {
        return repository.list(user).stream().map(row->{
            var item=new LinkedHashMap<String,Object>();
            item.put("jobId",row.get("job_id")); item.put("result",mapper.readTree((String)row.get("result_json")));
            item.put("scriptText",row.get("script_text")); item.put("createdAt",row.get("created_at"));
            item.put("storageStatus",row.get("archived_at")==null?"SAVING":"SAVED"); return (Map<String,Object>)item;
        }).toList();
    }
    private static UUID uuid(String raw){try{return UUID.fromString(raw);}catch(IllegalArgumentException e){throw new RunPodContractException(422,"DIRECT_HISTORY_INVALID");}}
    private static void require(boolean ok){if(!ok)throw new RunPodContractException(422,"DIRECT_HISTORY_INVALID");}
    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
