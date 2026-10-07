package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.voice.analysis.infrastructure.runpod.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded transport DTO. Parsing and hashing happen before the database transaction. */
public record CanonicalHandoffDocument(JsonNode metadata, byte[] metadataBytes,
        CanonicalCallbackDocument projection, String digest, Map<String,byte[]> inline) {
    public static CanonicalHandoffDocument parse(byte[] raw, RunPodContract contract) {
        var node=contract.parse(raw,"handoff"); var meta=node.get("metadata");
        var projection=CanonicalCallbackDocument.parse(bytes(node.get("projection")),contract);
        if(!RunPodContract.handoffResult(projection.schemaVersion()))invalid();
        var id=projection.identity();
        if(!id.eventId().equals(uuid(meta,"eventId")) || !id.requestId().equals(uuid(meta,"requestId"))
            || !id.executionId().equals(uuid(meta,"executionId")) || !id.workerId().equals(uuid(meta,"workerInstanceId"))
            || id.analysisId()!=meta.path("analysisId").asLong() || id.recordingId()!=meta.path("recordingId").asLong()
            || id.contentId()!=meta.path("contentId").asLong()
            || !projection.payloadSha256().equals(meta.path("projectionSha256").asText())
            || !contract.digest(meta).equals(node.path("handoffSha256").asText()))invalid();
        var kinds=new HashSet<String>();
        for(var item:meta.get("artifacts"))if(!kinds.add(item.path("kind").asText()))invalid();
        if (RunPodContract.RESULT_V6.equals(projection.schemaVersion()) != "voice-coaching.canonical-handoff.v2".equals(meta.path("schemaVersion").asText())) invalid();
        var required=RunPodContract.RESULT_V6.equals(projection.schemaVersion())
            ? Set.of("CORE","BRIDGE_RESULT","BINDING","ASSOCIATION","MEDIA_RECEIPT","VISUAL_EVIDENCE")
            : Set.of("CORE","BRIDGE_RESULT","BINDING","ASSOCIATION");
        if(projection.decision()==null ? !kinds.isEmpty() : !kinds.containsAll(required))invalid();
        var inline=new HashMap<String,byte[]>();
        node.get("inlineArtifacts").fields().forEachRemaining(e -> {
            if(!kinds.contains(e.getKey()))invalid();
            try { inline.put(e.getKey(),Base64.getDecoder().decode(e.getValue().asText())); }
            catch(IllegalArgumentException error){invalid();}
        });
        for(var item:meta.get("artifacts")) {
            var value=inline.get(item.path("kind").asText());
            if(value!=null)checkBytes(item,value);
        }
        return new CanonicalHandoffDocument(meta,bytes(meta),projection,node.path("handoffSha256").asText(),Map.copyOf(inline));
    }
    public UUID id(){return uuid(metadata,"handoffId");}
    public long reservedBytes(){long size=metadataBytes.length+projection.bytes().length;for(var a:metadata.get("artifacts"))size+=a.path("byteSize").asLong();return size;}
    public static void checkBytes(JsonNode meta,byte[] raw){
        if(raw.length!=meta.path("byteSize").asInt() || !CanonicalCallbackDocument.sha256(raw).equals(meta.path("sha256").asText()))invalid();
    }
    public static byte[] bytes(JsonNode node){return node.toString().getBytes(StandardCharsets.UTF_8);}
    public static UUID uuid(JsonNode node,String key){return UUID.fromString(node.path(key).asText());}
    private static void invalid(){throw new RunPodContractException(422,"VALIDATION_FAILED");}
    @Override public String toString(){return "CanonicalHandoffDocument[private]";}
}
