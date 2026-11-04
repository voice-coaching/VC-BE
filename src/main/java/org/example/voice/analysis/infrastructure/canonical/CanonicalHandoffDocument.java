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

        var id=projection.identity();

        var kinds=new HashSet<String>();
        for(var item:meta.get("artifacts")){}

        var required=new HashSet<>(Set.of("CORE","BRIDGE_RESULT","BINDING","ASSOCIATION"));
        if (RunPodContract.RESULT_V6.equals(projection.schemaVersion())) {
            var media=node.get("projection").path("mediaBinding");
            if(media.hasNonNull("videoSha256")) required.add("VISUAL_EVIDENCE");
            if(media.hasNonNull("mediaReceiptSha256")) required.add("MEDIA_RECEIPT");

        }

        var inline=new HashMap<String,byte[]>();
        node.get("inlineArtifacts").fields().forEachRemaining(e -> {

            try { inline.put(e.getKey(),Base64.getDecoder().decode(e.getValue().asText())); }
            catch(IllegalArgumentException error){}
        });
        for(var item:meta.get("artifacts")) {
            var value=inline.get(item.path("kind").asText());
        }
        return new CanonicalHandoffDocument(meta,bytes(meta),projection,node.path("handoffSha256").asText(),Map.copyOf(inline));
    }
    public UUID id(){return uuid(metadata,"handoffId");}
    public long reservedBytes(){long size=metadataBytes.length+projection.bytes().length;for(var a:metadata.get("artifacts"))size+=a.path("byteSize").asLong();return size;}
    public static void checkBytes(JsonNode meta,byte[] raw){
        if (raw==null || !CanonicalDeliverySpool.KINDS.contains(meta.path("kind").asText())
                || raw.length<1 || raw.length>16*1024*1024 || raw.length!=meta.path("byteSize").asLong()
                || !CanonicalCallbackDocument.sha256(raw).equals(meta.path("sha256").asText()))
            throw new RunPodContractException(422,"PAYLOAD_DIGEST_MISMATCH");
    }
    public static byte[] bytes(JsonNode node){return node.toString().getBytes(StandardCharsets.UTF_8);}
    public static UUID uuid(JsonNode node,String key){return UUID.fromString(node.path(key).asText());}

    @Override public String toString(){return "CanonicalHandoffDocument[private]";}
}
