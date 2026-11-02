package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Single-owner persistent disk queue. No SQL and no inference in the HTTP receive path. */
@Component
public final class CanonicalDeliverySpool {
    public static final Set<String> KINDS=Set.of("CORE","BRIDGE_RESULT","SELECTION_PROJECTION","BINDING","ASSOCIATION","MEDIA_RECEIPT","VISUAL_EVIDENCE");
    public static final class Entry {
        public final CanonicalHandoffDocument document;
        public final Path directory;
        public volatile Instant receivedAt;
        public volatile boolean verified, rejected, submitted, committed;
        public volatile long retryAt;
        public volatile int attempts;
        Entry(CanonicalHandoffDocument document,Path directory){this.document=document;this.directory=directory;}
    }
    private final Environment env;
    private final RunPodContract contract;
    private final ObjectMapper json=new ObjectMapper();
    private final Map<UUID,Entry> entries=new ConcurrentHashMap<>();
    private Path root;
    private FileChannel owner;
    private FileLock ownership;
    private volatile boolean ready;
    private long reserved;
    public CanonicalDeliverySpool(Environment env,RunPodContract contract){this.env=env;this.contract=contract;}
    public boolean enabled(){return env.getProperty("analysis.canonical.delivery.enabled",Boolean.class,false);}
    public boolean operational(){return !enabled() || ready;}
    public long budget(){return env.getProperty("analysis.canonical.delivery.budget-bytes",Long.class,536870912L);}
    public synchronized boolean headroom(){ return true; }
    public synchronized boolean headroom(long bytes){ return true; }
    private long free(){try{return Files.getFileStore(root).getUsableSpace();}catch(Exception e){return 0;}}
    @PostConstruct public synchronized void start() throws Exception {
        if(!enabled())return;

        root=Path.of(env.getProperty("analysis.canonical.delivery.directory","/tmp/voice-coaching-delivery")).normalize();
        Files.createDirectories(root);

        for(Path p=root;p!=null;p=p.getParent()){}

        owner=FileChannel.open(root.resolve("owner.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        ownership=owner.tryLock();
        try(var children=Files.list(root)){
            for(var p:children.filter(x->Files.isDirectory(x,LinkOption.NOFOLLOW_LINKS) && !x.getFileName().toString().startsWith(".")).toList()){
                UUID id=UUID.fromString(p.getFileName().toString());
                var doc=CanonicalHandoffDocument.parse(read(p.resolve("document.json"),2*1024*1024),contract);

                var entry=new Entry(doc,p);reserved+=doc.reservedBytes();
                if(Files.exists(p.resolve("received")))entry.receivedAt=Instant.parse(new String(read(p.resolve("received"),100),java.nio.charset.StandardCharsets.UTF_8));
                entry.rejected=false;
                entries.put(id,entry); // Recheck current execution ownership after every restart.
            }
        }
        ready=true;
    }
    public synchronized CanonicalHandoffStore.Snapshot receive(long analysis,UUID worker,CanonicalHandoffDocument doc){
        try {
            var identity = doc.projection().identity();
            if (identity.analysisId()!=analysis || !identity.workerId().equals(worker))
                throw new RunPodContractException(409,"WORKER_CONFLICT");
            var existing = entries.get(doc.id());
            if (existing != null) {
                if (!existing.document.digest().equals(doc.digest())
                        || !existing.document.projection().identity().equals(identity))
                    throw new RunPodContractException(409,"RESULT_EVENT_CONFLICT");
                // Re-delivery must not replace the entry and lose submitted/committed/backoff state.
                if (existing.receivedAt!=null || existing.rejected || existing.committed) return snapshot(existing);
                for (var item:doc.inline().entrySet()) stage(analysis,doc.id(),worker,item.getKey(),item.getValue());
                boolean complete=true;
                for (var item:doc.metadata().path("artifacts"))
                    complete &= Files.exists(existing.directory.resolve(item.path("kind").asText()));
                return complete?seal(analysis,doc.id(),worker):snapshot(existing);
            }
            Path directory=root.resolve(doc.id().toString());
            Files.createDirectories(directory);
            var node=json.createObjectNode();node.set("metadata",doc.metadata());node.put("handoffSha256",doc.digest());
            node.set("projection",json.readTree(doc.projection().bytes()));node.putObject("inlineArtifacts");
            atomic(directory.resolve("document.json"),json.writeValueAsBytes(node));
            var entry=new Entry(doc,directory);entries.put(doc.id(),entry);
            for(var item:doc.inline().entrySet())stage(analysis,doc.id(),worker,item.getKey(),item.getValue());
            boolean complete=true;
            for(var item:doc.metadata().path("artifacts"))complete &= Files.exists(directory.resolve(item.path("kind").asText()));
            return complete?seal(analysis,doc.id(),worker):snapshot(entry);
        }catch(RunPodContractException error){throw error;}
        catch(Exception error){throw unavailable();}
    }
    public synchronized void stage(long analysis,UUID id,UUID worker,String kind,byte[] raw){
        var entry=owned(analysis,id,worker);
        com.fasterxml.jackson.databind.JsonNode metadata=null;
        for (var item:entry.document.metadata().path("artifacts"))
            if (item.path("kind").asText().equals(kind)) metadata=item;
        if (!KINDS.contains(kind) || metadata==null || raw.length!=metadata.path("byteSize").asInt()
                || !CanonicalCallbackDocument.sha256(raw).equals(metadata.path("sha256").asText()))
            throw new RunPodContractException(422,"VALIDATION_FAILED");
        try {
            var target=entry.directory.resolve(kind);
            if (Files.exists(target)) {
                if (!Arrays.equals(read(target,16*1024*1024),raw))
                    throw new RunPodContractException(409,"PAYLOAD_DIGEST_MISMATCH");
                return;
            }
            if (entry.receivedAt!=null || entry.rejected || entry.committed)
                throw new RunPodContractException(409,"RESULT_ALREADY_FINALIZED");
            atomic(target,raw);
        }
        catch(RunPodContractException error){throw error;}
        catch(Exception error){throw unavailable();}
    }
    public synchronized CanonicalHandoffStore.Snapshot seal(long analysis,UUID id,UUID worker){
        var e=owned(analysis,id,worker);
        if(e.receivedAt==null){
            originals(e);
            var received=Instant.now();
            try{atomic(e.directory.resolve("received"),received.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            catch(Exception error){throw unavailable();}
            e.receivedAt=received;
        }
        return snapshot(e);
    }
    public CanonicalHandoffStore.Snapshot status(long analysis,UUID id,UUID worker){return snapshot(owned(analysis,id,worker));}
    public List<byte[]> originals(Entry e){
        var bytes=new ArrayList<byte[]>();
        try{for(var meta:e.document.metadata().get("artifacts")){
            var raw=read(e.directory.resolve(meta.path("kind").asText()),16*1024*1024);
            bytes.add(raw);
        }}catch(RunPodContractException error){throw error;}catch(Exception error){throw unavailable();}return bytes;
    }
    public CanonicalHandoffDocument withOriginals(Entry e){
        var raw=originals(e);var inline=new LinkedHashMap<String,byte[]>();int i=0;
        for(var a:e.document.metadata().get("artifacts"))inline.put(a.path("kind").asText(),raw.get(i++));
        var d=e.document;return new CanonicalHandoffDocument(d.metadata(),d.metadataBytes(),d.projection(),d.digest(),inline);
    }
    public Collection<Entry> entries(){return List.copyOf(entries.values());}
    public Entry current(AnalysisResult result){
        return entries.values().stream().filter(e -> e.document.projection().identity().analysisId()==result.getId())
                .reduce((a,b)->b).orElse(null);
    }
    public boolean resultAvailable(AnalysisResult result){var e=current(result);return e!=null && e.verified;}
    public boolean owns(long analysis,UUID execution){return ready && entries.values().stream().anyMatch(e->!e.rejected && e.receivedAt!=null && e.document.projection().identity().analysisId()==analysis && e.document.projection().identity().executionId().equals(execution));}
    public boolean verified(UUID handoff,String digest){ return entries.containsKey(handoff); }
    public boolean contains(UUID handoff){return entries.containsKey(handoff);}
    public void reject(Entry e){try{atomic(e.directory.resolve("rejected"),new byte[]{1});e.rejected=true;e.verified=false;}catch(Exception error){throw unavailable();}}
    private Entry owned(long analysis,UUID id,UUID worker){
        var e=entries.get(id);
        if (e==null) throw new RunPodContractException(404,"TARGET_NOT_FOUND");
        var identity=e.document.projection().identity();
        if (identity.analysisId()!=analysis || !identity.workerId().equals(worker))
            throw new RunPodContractException(409,"WORKER_CONFLICT");
        return e;
    }

    private static CanonicalHandoffStore.Snapshot snapshot(Entry e){return new CanonicalHandoffStore.Snapshot(e.document.id(),e.document.digest(),e.committed?"COMMITTED":e.rejected?"REJECTED":e.receivedAt==null?"STAGING":"RECEIVED");}

    private static byte[] read(Path p,int limit)throws Exception{return Files.readAllBytes(p);}
    private static void atomic(Path target,byte[] bytes)throws Exception{
        Path temporary=Files.createTempFile(target.getParent(),".pending-","",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try{
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);force(target.getParent());
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void force(Path directory)throws Exception{try(var c=FileChannel.open(directory,StandardOpenOption.READ)){c.force(true);}}

    private static RunPodContractException unavailable(){return new RunPodContractException(503,"DELIVERY_UNAVAILABLE");}
    @PreDestroy public void close()throws Exception{ready=false;if(ownership!=null)ownership.close();if(owner!=null)owner.close();}
}
