package org.example.voice.practiceexample.infrastructure;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.example.voice.practiceexample.application.ExampleTtsProperties;
import org.example.voice.practiceexample.domain.ExampleTtsFailure;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.*;
import org.example.voice.practiceexample.domain.port.ExampleTtsGenerator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RunPodExampleTtsGenerator implements ExampleTtsGenerator {
    private final ExampleTtsProperties properties;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public RunPodExampleTtsGenerator(ExampleTtsProperties properties,ObjectMapper json) { this.properties=properties;this.json=json; }
    public record Request(String schemaVersion,String requestId,String synthesisRevision,String text,String language,String voice,double speakingRate,String format) {}
    @Override public Generated generate(Job job) {
        if(!properties.configured()) throw new ExampleTtsFailure("TTS_CONFIGURATION",false);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        String requestId=job.owner().toString();
        Path mp3=null;
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            URI uri=URI.create(properties.getBaseUrl().replaceAll("/+$", "")+"/v1/tts/synthesize");
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new ExampleTtsFailure("TTS_CONFIGURATION",false);
            var body=new Request("voice-coaching.tts-request.v1",requestId,job.profileRevision(),job.text(),"ko-KR",properties.getVoice(),properties.getSpeakingRate(),"mp3");
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+properties.getApiToken())
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            pending=http.sendAsync(request,info->new LimitedBody());
            var response=pending.get(25,TimeUnit.SECONDS);
            int status=response.statusCode();
            if(status==401) throw new ExampleTtsFailure("TTS_AUTH",false);
            if(status==409) throw new ExampleTtsFailure("TTS_REVISION",false);
            if(status!=200) throw new ExampleTtsFailure("TTS_HTTP_"+status,status==429 || status==503 || status==504);
            String mime=response.headers().firstValue("Content-Type").orElse("").split(";")[0].trim();
            byte[] bytes=response.body();
            String digest=ExampleTtsPersistence.hash(bytes);
            if(!mime.equalsIgnoreCase("audio/mpeg") || bytes.length<3
                    || !requestId.equals(response.headers().firstValue("X-Request-Id").orElse(""))
                    || !job.profileRevision().equals(response.headers().firstValue("X-TTS-Revision").orElse(""))
                    || !digest.equals(response.headers().firstValue("X-Audio-SHA256").orElse("")))
                throw new ExampleTtsFailure("TTS_INVALID_AUDIO",false);
            mp3=Files.createTempFile("example-tts-",".mp3");Files.write(mp3,bytes);
            run(List.of(properties.getDecoder(),"-nostdin","-v","error","-xerror","-i",mp3.toString(),"-f","null","-"),deadline,false);
            String probe=run(List.of(properties.getProbe(),"-v","error","-show_entries","stream=codec_name,channels,sample_rate:format=duration","-of","json",mp3.toString()),deadline,true);
            var info=json.readTree(probe); var streams=info.path("streams");
            double seconds=Double.parseDouble(info.path("format").path("duration").asText());
            int duration=(int)Math.round(seconds*1000);
            int claimed=Integer.parseInt(response.headers().firstValue("X-Audio-Duration-Ms").orElse("0"));
            if(streams.size()!=1 || !"mp3".equals(streams.get(0).path("codec_name").asText()) || streams.get(0).path("channels").asInt()!=1
                    || !Double.isFinite(seconds) || duration<=0 || duration>120000 || Math.abs(claimed-duration)>150)
                throw new ExampleTtsFailure("TTS_INVALID_AUDIO",false);
            return new Generated(bytes,digest,duration);
        } catch(ExampleTtsFailure e) { throw e; }
        catch(TimeoutException e){throw new ExampleTtsFailure("TTS_TIMEOUT",true);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new ExampleTtsFailure("TTS_INTERRUPTED",true);}
        catch(ExecutionException e){throw new ExampleTtsFailure(e.getCause() instanceof ExampleTtsFailure ? "TTS_INVALID_AUDIO":"TTS_NETWORK",!(e.getCause() instanceof ExampleTtsFailure));}
        catch(Exception e){throw new ExampleTtsFailure("TTS_VALIDATION",false);}
        finally {
            if(pending!=null && !pending.isDone()) pending.cancel(true);
            if(mp3!=null) try {Files.deleteIfExists(mp3);} catch(Exception ignored) { /* OS temporary directory cleanup is a secondary guard. */ }
        }
    }
    private String run(List<String> command,long deadline,boolean output) throws Exception {
        long remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
        if(remaining<=0) throw new TimeoutException();
        var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        if(!output) builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process=builder.start();
        try {
            if(!process.waitFor(Math.min(3000,remaining),TimeUnit.MILLISECONDS)) throw new TimeoutException();
            if(process.exitValue()!=0) throw new ExampleTtsFailure("TTS_DECODE",false);
            return output ? new String(process.getInputStream().readNBytes(8192),java.nio.charset.StandardCharsets.UTF_8):"";
        } finally { if(process.isAlive()) {process.destroyForcibly();process.waitFor(1,TimeUnit.SECONDS);} }
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(bytes.size()+buffer.remaining()>2_000_000){subscription.cancel();body.completeExceptionally(new ExampleTtsFailure("TTS_TOO_LARGE",false));return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){body.completeExceptionally(error);}
        public void onComplete(){body.complete(bytes.toByteArray());}
    }
}
