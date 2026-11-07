package org.example.voice.analysis.infrastructure.independent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

/** Independent transport DTO, not a legacy callback/handoff or a verifier receipt. */
public final class IndependentResultDocument {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final JsonNode node;
    private IndependentResultDocument(JsonNode node) { this.node=node; }
    public static IndependentResultDocument parse(byte[] raw,IndependentAnalysisAttempt attempt) {
        try {
            if(raw.length>2097152) throw invalid();
            String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            var node=JSON.readTree(decoded);var source=node.path("sourceIdentity");
            if(!node.isObject() || !attempt.aiExecutionId().equals(HttpIndependentAnalysisClient.uuid(source,"executionId"))
                    || !attempt.audioSha256().equals(source.path("audioSha256").asText())
                    || !attempt.scriptSha256().equals(source.path("scriptSha256").asText())) throw invalid();
            var score=node.path("score");
            if (!score.isMissingNode() && !score.isNull()) {
                if (!score.isObject() || !score.has("overallScore")) throw invalid();
                var overall=score.get("overallScore");
                if (!overall.isNull() && (!overall.isNumber() || overall.decimalValue().compareTo(BigDecimal.ZERO)<0
                        || overall.decimalValue().compareTo(BigDecimal.valueOf(100))>0)) throw invalid();
                for(String field:List.of("validity","reason"))
                    if(score.hasNonNull(field) && !score.get(field).isTextual()) throw invalid();
            }
            var feedback=node.path("coaching").path("feedback");
            if (!feedback.isMissingNode() && !feedback.isNull() && !feedback.isTextual()) throw invalid();
            return new IndependentResultDocument(node);
        } catch (Exception e) { throw invalid(); }
    }
    public BigDecimal score() { var n=node.path("score").path("overallScore");return n.isNumber()?n.decimalValue():null; }
    public String feedback() { return text(node.path("coaching"),"feedback"); }
    public String failure() {
        var f=node.path("failure");return f.isTextual()?f.asText():text(f,"code");
    }
    public boolean accepted() { return "ACCEPT".equals(node.path("decision").path("status").asText()); }
    public CanonicalAnalysisView.Canonical project() {
        try {
            var d=node.path("decision");
            var decision=d.isObject()?new CanonicalAnalysisView.Decision(text(d,"status"),text(d,"reason_code"),text(d,"stage")):null;
            var s=node.path("score");
            var score=s.isObject()?new CanonicalAnalysisView.Score(score(),text(s,"validity"),text(s,"reason"),
                    object(s.get("rubricRevision")),object(s.get("criteria")),s.hasNonNull("details")
                            ?JSON.treeToValue(s.get("details"),org.example.voice.analysis.domain.model.PublicScoreDetails.class):null):null;
            var c=node.path("coaching");CanonicalAnalysisView.Coaching coaching=null;
            if(c.isObject()) {
                String adapter=text(c,"adapterStatus");String generation=text(c,"generationStatus");
                if ("READY".equals(adapter) || feedback()!=null) {
                    List<CanonicalAnalysisView.CoachingItem> items=new ArrayList<>();
                    if(c.path("items").isArray()) for(var item:c.get("items")) items.add(JSON.treeToValue(item,CanonicalAnalysisView.CoachingItem.class));
                    coaching=new CanonicalAnalysisView.ReadyCoaching(text(c,"schemaVersion"),adapter,generation,items,
                            c.path("dispatchAttempts").asInt(),text(c,"fallbackReason"),false,false,feedback());
                } else if("FAIL_CLOSED".equals(adapter)) {
                    coaching=new CanonicalAnalysisView.FailedCoaching(text(c,"schemaVersion"),adapter,generation,List.of(),
                            c.path("dispatchAttempts").asInt(),text(c,"errorCode"));
                } else if(adapter!=null) coaching=new CanonicalAnalysisView.EmptyCoaching(text(c,"schemaVersion"),adapter,generation,List.of(),c.path("dispatchAttempts").asInt());
            }
            var selection=node.path("selection").isObject()?JSON.treeToValue(node.get("selection"),CanonicalAnalysisView.Selection.class):null;
            // Wrapper constants describe this public view, not fabricated AI evidence or lip scores.
            return new CanonicalAnalysisView.Canonical("INLINE",decision,text(node,"coreStatus"),accepted(),
                    "REFINED_P1_ONLY",selection,coaching,score,
                    new CanonicalAnalysisView.LipVisual("NOT_PROVIDED",false,null,"NONE",
                            new CanonicalAnalysisView.LipCoverage(0,0,0,0,null),List.of(),List.of("VIDEO_NOT_PROVIDED"),
                            "CANONICAL_MFA_DETECTOR_PHONES_V1",List.of(),0));
        } catch (Exception e) { throw invalid(); }
    }
    private static Object object(JsonNode n) { return n==null||n.isNull()?null:JSON.convertValue(n,Object.class); }
    private static String text(JsonNode n,String key) { return HttpIndependentAnalysisClient.text(n,key); }
    public static String sha256(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
    private static IndependentTransportException invalid() { return new IndependentTransportException(0,"INDEPENDENT_RESULT_INVALID",0); }
    @Override public String toString() { return "IndependentResultDocument[private]"; }
}
