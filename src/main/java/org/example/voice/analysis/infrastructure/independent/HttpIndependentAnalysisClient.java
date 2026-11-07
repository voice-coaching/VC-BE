package org.example.voice.analysis.infrastructure.independent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;
import org.example.voice.analysis.domain.port.IndependentAnalysisClient;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Component
public final class HttpIndependentAnalysisClient implements IndependentAnalysisClient {
    private final IndependentAnalysisProperties properties;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public HttpIndependentAnalysisClient(IndependentAnalysisProperties properties) { this.properties=properties; }
    public Optional<Job> find(UUID request) {
        var response=exchange("GET","/v2/analysis-jobs?clientRequestId="+request,null,Map.of(),131072);
        if (response.statusCode()==404) return Optional.empty();
        require(response,200);return Optional.of(job(response.body()));
    }
    public Job submit(IndependentAnalysisAttempt attempt,byte[] wav,String script) {
        try {
            var metadata=json.createObjectNode().put("clientRequestId",attempt.clientRequestId().toString())
                    .put("scriptText",script).put("audioSha256",attempt.audioSha256())
                    .put("contentId",attempt.contentId());
            String encoded=Base64.getEncoder().encodeToString(json.writeValueAsBytes(metadata));
            if (encoded.length()>24000) throw new IndependentTransportException(422,"METADATA_TOO_LARGE",0);
            var response=exchange("POST","/v2/analysis-jobs",wav,
                    Map.of("Content-Type","audio/wav","X-Analysis-Metadata",encoded),131072);
            require(response,202);return job(response.body());
        } catch (IndependentTransportException e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }
    public Job status(UUID id) {
        var r=exchange("GET","/v2/analysis-jobs/"+id,null,Map.of(),131072);require(r,200);return job(r.body());
    }
    public Result result(UUID id) {
        var r=exchange("GET","/v2/analysis-jobs/"+id+"/result",null,Map.of(),2097152);require(r,200);
        String declared=r.headers().firstValue("X-Result-SHA256").orElse("");
        String actual=IndependentResultDocument.sha256(r.body());
        if (!actual.equals(declared)) throw new IndependentTransportException(0,"RESULT_DIGEST_MISMATCH",0);
        return new Result(r.body(),actual);
    }
    public Job acknowledge(UUID id,String sha) {
        var body=("{\"resultSha256\":\""+sha+"\"}").getBytes(StandardCharsets.UTF_8);
        var r=exchange("POST","/v2/analysis-jobs/"+id+"/ack",body,Map.of("Content-Type","application/json"),131072);
        require(r,200);return job(r.body());
    }
    public Job cancel(UUID id) {
        var r=exchange("POST","/v2/analysis-jobs/"+id+"/cancel",null,Map.of(),131072);
        if(r.statusCode()!=202) require(r,200);
        return job(r.body());
    }
    private Job job(byte[] raw) {
        try {
            String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            var n=json.readTree(decoded);
            if (!"voice-coaching.independent-analysis.v1".equals(n.path("schemaVersion").asText())
                    || !Set.of("QUEUED","RUNNING","COMPLETED","FAILED","CANCELLED").contains(n.path("status").asText())
                    || !n.path("sequence").isIntegralNumber() || !n.path("sequence").canConvertToLong()
                    || n.path("sequence").asLong()<1 || !n.path("resultAvailable").isBoolean()) throw invalid();
            OffsetDateTime.parse(n.path("createdAt").asText());OffsetDateTime.parse(n.path("updatedAt").asText());
            String digest=text(n,"resultSha256");
            if ((digest!=null && !digest.matches("[a-f0-9]{64}")) || (n.path("resultAvailable").asBoolean() && digest==null)) throw invalid();
            var failure=n.path("failure");
            return new Job(uuid(n,"clientRequestId"),uuid(n,"jobId"),uuid(n,"executionId"),n.path("status").asText(),
                    n.path("sequence").asLong(),n.path("resultAvailable").asBoolean(),digest,text(failure,"code"),
                    text(n,"acknowledgedAt")==null?null:OffsetDateTime.parse(text(n,"acknowledgedAt")));
        } catch (Exception e) { throw invalid(); }
    }
    static UUID uuid(JsonNode node,String name) {
        String s=node.path(name).asText();var id=UUID.fromString(s);
        if (!id.toString().equals(s)) throw invalid();
        return id;
    }
    static String text(JsonNode n,String key) { return n.path(key).isTextual()?n.path(key).textValue():null; }
    private HttpResponse<byte[]> exchange(String method,String path,byte[] body,Map<String,String> headers,int limit) {
        var request=HttpRequest.newBuilder(properties.uri(path)).timeout(properties.getHttpTimeout())
                .header("Authorization","Bearer "+properties.getApiToken()).header("Accept-Encoding","identity")
                .header("Content-Encoding","identity");
        headers.forEach(request::header);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        var future=http.sendAsync(request.build(),info->new BoundedBody(limit));
        try {
            var r=future.get(properties.getHttpTimeout().toMillis(),TimeUnit.MILLISECONDS);
            if (!r.headers().firstValue("content-encoding").orElse("identity").equalsIgnoreCase("identity")) throw invalid();
            return r;
        } catch (IndependentTransportException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IndependentTransportException(0,"AI_IO_INTERRUPTED",0); }
        catch (Exception e) { throw new IndependentTransportException(0,"AI_IO_UNAVAILABLE",0); }
        finally { future.cancel(true); }
    }
    private void require(HttpResponse<byte[]> response,int expected) {
        if (response.statusCode()==expected) return;
        String code="AI_HTTP_"+response.statusCode();
        try {
            var value=json.readTree(response.body()).path("reasonCode");
            if (value.isTextual() && value.asText().matches("[A-Za-z0-9_.:-]{1,100}")) code=value.asText();
        } catch (Exception ignored) { /* Remote response text is never propagated. */ }
        long retry=0;
        String header=response.headers().firstValue("Retry-After").orElse("");
        try { retry=Long.parseLong(header); }
        catch (NumberFormatException ignored) {
            try { retry=Duration.between(Instant.now(),ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds(); }
            catch (Exception absent) { /* Backoff applies without a valid header. */ }
        }
        throw new IndependentTransportException(response.statusCode(),code,Math.clamp(retry,0,86400));
    }
    private static IndependentTransportException invalid() { return new IndependentTransportException(0,"AI_RESPONSE_INVALID",0); }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private final int limit;private Flow.Subscription subscription;
        BoundedBody(int limit) { this.limit=limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s;s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>limit-bytes.size()) { subscription.cancel();result.completeExceptionally(invalid());return; }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) { result.completeExceptionally(e); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    @PreDestroy public void close() { http.shutdownNow(); }
}
