package org.example.voice.admin.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.model.AdminAudioData;
import org.example.voice.common.exception.*;
import org.example.voice.training.infrastructure.storage.MediaNormalizationProperties;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** No user-controlled command, input URL, remote protocol, or media output file. */
@Component @RequiredArgsConstructor
public class ReferenceAudioInspector {
    private final MediaNormalizationProperties media;
    private static final int PROBE_LIMIT=64*1024;

    public void assertAvailable() { binary(media.getFfmpegBinary());binary(media.getFfprobeBinary()); }

    public int validate(byte[] bytes,String extension) {
        if(bytes.length<1 || bytes.length>AdminAudioData.MAX_BYTES)throw invalid();
        Path workspace=null;
        try {
            workspace=Files.createTempDirectory("voice-admin-reference-");
            Path source=workspace.resolve("source"+extension);Files.write(source,bytes,StandardOpenOption.CREATE_NEW);
            byte[] probe=execute(List.of(binary(media.getFfprobeBinary()),"-v","error","-max_alloc","67108864",
                    "-protocol_whitelist","file,pipe","-show_entries","format=format_name,duration:stream=codec_type,codec_name,channels,sample_rate",
                    "-of","json",source.toString()),true,workspace);
            int duration=duration(probe,extension);
            // Probe metadata alone is not proof of a decodable file: decode all audio frames.
            execute(List.of(binary(media.getFfmpegBinary()),"-v","error","-nostdin","-xerror","-max_alloc","67108864",
                    "-protocol_whitelist","file,pipe","-threads","1","-err_detect","explode","-i",source.toString(),
                    "-map","0:a:0","-vn","-sn","-dn","-f","null","-"),false,workspace);
            return duration;
        } catch(BaseException error) { throw error; }
        catch(InterruptedException error) { Thread.currentThread().interrupt();throw unavailable(); }
        catch(IOException error) { throw unavailable(); }
        finally {
            if(workspace!=null) {
                try(var files=Files.walk(workspace)) {
                    for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);
                } catch(IOException error) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("admin_reference_audio_temporary_cleanup_failed");
                }
            }
        }
    }
    int duration(byte[] raw,String extension) {
        try {
            if(raw.length>PROBE_LIMIT)throw invalid();
            var node=new ObjectMapper().readTree(raw);var streams=node.path("streams");var format=node.path("format");
            if(!streams.isArray() || streams.size()!=1)throw invalid();
            var stream=streams.get(0);String codec=stream.path("codec_name").asText();String container=format.path("format_name").asText();
            boolean supported=(".mp3".equals(extension) && "mp3".equals(codec) && "mp3".equals(container))
                    || (".m4a".equals(extension) && "aac".equals(codec) && Arrays.asList(container.split(",")).contains("mov"));
            double seconds=Double.parseDouble(format.path("duration").asText());
            int channels=stream.path("channels").asInt();int rate=Integer.parseInt(stream.path("sample_rate").asText());
            if(!supported || !"audio".equals(stream.path("codec_type").asText()) || channels<1 || channels>2
                    || rate<8000 || rate>192000 || !Double.isFinite(seconds) || seconds<0.5 || seconds>AdminAudioData.MAX_DURATION_MS/1000.0)
                throw invalid();
            return (int)Math.round(seconds*1000);
        } catch(BaseException error) { throw error; }
        catch(Exception error) { throw invalid(); }
    }
    byte[] execute(List<String> command,boolean capture,Path directory) throws IOException,InterruptedException {
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        if(!capture)builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        String systemRoot=System.getenv("SystemRoot");builder.environment().clear();
        builder.environment().put("LANG","C");builder.environment().put("LC_ALL","C");
        builder.environment().put("PATH","/usr/bin:/bin");if(systemRoot!=null)builder.environment().put("SystemRoot",systemRoot);
        Process process=builder.start();byte[][] output={new byte[0]};Thread reader=null;
        if(capture)reader=Thread.startVirtualThread(()->{
            try(var stream=process.getInputStream()) { output[0]=stream.readNBytes(PROBE_LIMIT+1); }
            catch(IOException ignored) { output[0]=null; }
        });
        try {
            long timeout=Math.max(1,Math.min(30_000,media.getProcessTimeout().toMillis()));
            if(!process.waitFor(timeout,TimeUnit.MILLISECONDS))throw invalid();
            if(reader!=null)reader.join(2000);
            if((reader!=null && reader.isAlive()) || output[0]==null || output[0].length>PROBE_LIMIT || process.exitValue()!=0)throw invalid();
            return output[0];
        } finally {
            process.descendants().forEach(child -> child.destroyForcibly());
            if(process.isAlive()) { process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS); }
            if(reader!=null && reader.isAlive())reader.interrupt();
        }
    }
    private static String binary(String path) {
        try {
            if(path==null || !Path.of(path).isAbsolute() || !Files.isExecutable(Path.of(path)))throw unavailable();return path;
        } catch(InvalidPathException error) { throw unavailable(); }
    }
    private static BaseException invalid() { return new BaseException(ErrorCode.ADMIN_AUDIO_INVALID); }
    private static BaseException unavailable() { return new BaseException(ErrorCode.ADMIN_AUDIO_UNAVAILABLE); }
}
