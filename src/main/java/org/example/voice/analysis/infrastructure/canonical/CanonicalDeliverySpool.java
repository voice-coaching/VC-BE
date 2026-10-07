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
    public synchronized boolean headroom(){return headroom(CanonicalHandoffSettings.RESERVATION);}
    public synchronized boolean headroom(long bytes){return bytes>0 && operational() && (!enabled() || (reserved+bytes<=budget() && free()>=bytes));}
    private long free(){try{return Files.getFileStore(root).getUsableSpace();}catch(Exception e){return 0;}}
    @PostConstruct public synchronized void start() throws Exception {
        if(!enabled())return;
        if(budget()<85065728L || budget()>17179869184L)throw new IllegalStateException("DELIVERY_BUDGET_INVALID");
        root=Path.of(env.getRequiredProperty("analysis.canonical.delivery.directory")).normalize();
        if(!root.isAbsolute() || !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("DELIVERY_DIRECTORY_REQUIRED");
        for(Path p=root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IllegalStateException("DELIVERY_SYMLINK");
        if(!Files.getPosixFilePermissions(root).equals(PosixFilePermissions.fromString("rwx------")))throw new IllegalStateException("DELIVERY_DIRECTORY_PERMISSIONS");
        if(Files.isSymbolicLink(root.resolve("owner.lock")))throw new IllegalStateException("DELIVERY_SYMLINK");
        owner=FileChannel.open(root.resolve("owner.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        ownership=owner.tryLock();if(ownership==null)throw new IllegalStateException("DELIVERY_ALREADY_OWNED");
        try(var children=Files.list(root)){
            for(var p:children.filter(x->Files.isDirectory(x,LinkOption.NOFOLLOW_LINKS) && !x.getFileName().toString().startsWith(".")).toList()){
                UUID id=UUID.fromString(p.getFileName().toString());
                var doc=CanonicalHandoffDocument.parse(read(p.resolve("document.json"),2*1024*1024),contract);
                if(!id.equals(doc.id()))throw new IllegalStateException("DELIVERY_ID_MISMATCH");
                var entry=new Entry(doc,p);reserved+=doc.reservedBytes();
                if(Files.exists(p.resolve("received")))entry.receivedAt=Instant.parse(new String(read(p.resolve("received"),100),java.nio.charset.StandardCharsets.UTF_8));
                entry.rejected=Files.exists(p.resolve("rejected"));
                entries.put(id,entry); // Recheck current execution ownership after every restart.
            }
        }
        ready=true;
    }
    public synchronized CanonicalHandoffStore.Snapshot receive(long analysis,UUID worker,CanonicalHandoffDocument doc){
        requireReady();checkOwner(analysis,worker,doc);
        Entry entry=entries.get(doc.id());
        try{
            if(entry==null){
                if(entries.values().stream().anyMatch(e->e.document.projection().identity().executionId().equals(doc.projection().identity().executionId())))fail(409,"RESULT_EVENT_CONFLICT");
                if(reserved+doc.reservedBytes()>budget() || free()<doc.reservedBytes()+1048576)fail(429,"CAPACITY_EXCEEDED");
                var directory=root.resolve(doc.id().toString());
                var staging=Files.createTempDirectory(root,".staging-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                var node=json.createObjectNode();node.set("metadata",doc.metadata());node.put("handoffSha256",doc.digest());
                node.set("projection",json.readTree(doc.projection().bytes()));node.putObject("inlineArtifacts");
                atomic(staging.resolve("document.json"),json.writeValueAsBytes(node));
                Files.move(staging,directory,StandardCopyOption.ATOMIC_MOVE);
                var header=new CanonicalHandoffDocument(doc.metadata(),doc.metadataBytes(),doc.projection(),doc.digest(),Map.of());
                entry=new Entry(header,directory);entries.put(doc.id(),entry);reserved+=doc.reservedBytes();
                force(root);
            }else if(!entry.document.digest().equals(doc.digest()))fail(409,"RESULT_EVENT_CONFLICT");
            for(var item:doc.inline().entrySet())stage(analysis,doc.id(),worker,item.getKey(),item.getValue());
            boolean complete=true;for(var a:doc.metadata().get("artifacts"))complete &= Files.isRegularFile(entry.directory.resolve(a.path("kind").asText()),LinkOption.NOFOLLOW_LINKS);
            return complete?seal(analysis,doc.id(),worker):snapshot(entry);
        }catch(RunPodContractException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public synchronized void stage(long analysis,UUID id,UUID worker,String kind,byte[] raw){
        var e=owned(analysis,id,worker);
        if(!KINDS.contains(kind))fail(422,"VALIDATION_FAILED");
        var meta=e.document.metadata().get("artifacts");
        com.fasterxml.jackson.databind.JsonNode found=null;for(var a:meta)if(kind.equals(a.path("kind").asText()))found=a;
        if(found==null)fail(422,"VALIDATION_FAILED");CanonicalHandoffDocument.checkBytes(found,raw);
        try{
            var path=e.directory.resolve(kind);
            if(Files.exists(path)){if(!Arrays.equals(read(path,16*1024*1024),raw))fail(409,"PAYLOAD_DIGEST_MISMATCH");return;}
            if(e.receivedAt!=null || e.rejected)fail(409,"RESULT_ALREADY_FINALIZED");
            atomic(path,raw);
        }catch(RunPodContractException error){throw error;}catch(Exception error){throw unavailable();}
    }
    public synchronized CanonicalHandoffStore.Snapshot seal(long analysis,UUID id,UUID worker){
        var e=owned(analysis,id,worker);if(e.rejected)fail(409,"RESULT_ALREADY_FINALIZED");
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
            CanonicalHandoffDocument.checkBytes(meta,raw);bytes.add(raw);
        }}catch(RunPodContractException error){throw error;}catch(Exception error){throw unavailable();}return bytes;
    }
    public CanonicalHandoffDocument withOriginals(Entry e){
        var raw=originals(e);var inline=new LinkedHashMap<String,byte[]>();int i=0;
        for(var a:e.document.metadata().get("artifacts"))inline.put(a.path("kind").asText(),raw.get(i++));
        var d=e.document;return new CanonicalHandoffDocument(d.metadata(),d.metadataBytes(),d.projection(),d.digest(),inline);
    }
    public Collection<Entry> entries(){return List.copyOf(entries.values());}
    public Entry current(AnalysisResult result){
        if(!enabled() || !ready || !result.isCanonicalExecution() || !Set.of("PENDING","PROCESSING").contains(result.getStatus().name()))return null;
        return entries.values().stream().filter(e->!e.rejected && e.receivedAt!=null &&
            result.isForActiveRequest(e.document.projection().identity().requestId()) &&
            result.isForActiveExecution(e.document.projection().identity().executionId()) &&
            e.document.projection().identity().workerId().toString().equals(result.getWorkerInstanceId())).findFirst().orElse(null);
    }
    public boolean resultAvailable(AnalysisResult result){var e=current(result);return e!=null && e.verified;}
    public boolean owns(long analysis,UUID execution){return ready && entries.values().stream().anyMatch(e->!e.rejected && e.receivedAt!=null && e.document.projection().identity().analysisId()==analysis && e.document.projection().identity().executionId().equals(execution));}
    public boolean verified(UUID handoff,String digest){var e=entries.get(handoff);return e!=null && e.verified && !e.rejected && e.document.digest().equals(digest);}
    public boolean contains(UUID handoff){return entries.containsKey(handoff);}
    public void reject(Entry e){try{atomic(e.directory.resolve("rejected"),new byte[]{1});e.rejected=true;e.verified=false;}catch(Exception error){throw unavailable();}}
    private Entry owned(long analysis,UUID id,UUID worker){requireReady();var e=entries.get(id);if(e==null)fail(404,"TARGET_NOT_FOUND");checkOwner(analysis,worker,e.document);return e;}
    private static void checkOwner(long analysis,UUID worker,CanonicalHandoffDocument d){if(analysis!=d.projection().identity().analysisId() || !worker.equals(d.projection().identity().workerId()))fail(409,"WORKER_CONFLICT");}
    private static CanonicalHandoffStore.Snapshot snapshot(Entry e){return new CanonicalHandoffStore.Snapshot(e.document.id(),e.document.digest(),e.committed?"COMMITTED":e.rejected?"REJECTED":e.receivedAt==null?"STAGING":"RECEIVED");}
    private void requireReady(){if(!ready)throw unavailable();}
    private static byte[] read(Path p,int limit)throws Exception{if(Files.isSymbolicLink(p)||Files.size(p)>limit)throw unavailable();return Files.readAllBytes(p);}
    private static void atomic(Path target,byte[] bytes)throws Exception{
        Path temporary=Files.createTempFile(target.getParent(),".pending-","",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try{
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);force(target.getParent());
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void force(Path directory)throws Exception{try(var c=FileChannel.open(directory,StandardOpenOption.READ)){c.force(true);}}
    private static void fail(int status,String reason){throw new RunPodContractException(status,reason);}
    private static RunPodContractException unavailable(){return new RunPodContractException(503,"DELIVERY_UNAVAILABLE");}
    @PreDestroy public void close()throws Exception{ready=false;if(ownership!=null)ownership.close();if(owner!=null)owner.close();}
}
