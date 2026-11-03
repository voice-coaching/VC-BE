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
        var job=uuid(doc.path("jobId").asText());
        var execution=uuid(doc.path("executionId").asText());
        var result=doc.path("result");
        repository.receive(job,execution,doc.path("historyClaimSha256").asText(null),doc.path("resultSha256").asText(null),
                result.toString(),doc.path("scriptText").asText(null),
                doc.path("contentId").isNumber()?doc.path("contentId").asLong():null,
                doc.path("archive").isObject()?doc.path("archive").toString():null);
    }
    @Transactional
    public String link(long user, UUID job, String claim) { repository.link(user,job,claim == null ? null : sha(claim)); return repository.state(user,job); }
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

    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
