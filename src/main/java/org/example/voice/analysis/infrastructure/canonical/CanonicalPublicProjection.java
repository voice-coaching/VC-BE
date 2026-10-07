package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.*;
import org.example.voice.analysis.exception.CanonicalAnalysisViewException;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.example.voice.analysis.exception.CanonicalAnalysisViewException.Reason.CANONICAL_RESULT_UNAVAILABLE;

/** Projection of an already committed/verified document, not a semantic verifier or public raw JSON relay. */
@Component
public final class CanonicalPublicProjection {
    private final RunPodContract contract;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public CanonicalPublicProjection(RunPodContract contract) { this.contract=contract; }

    public Canonical project(CanonicalCallbackDocument document) {
        if (document.decision()==null) return null;
        try {
            var root=contract.parse(document.bytes(),"result");
            var decision=document.decision();
            var selection=nullable(root.get("selection"),Selection.class);
            if (selection!=null) checkBrowserNumbers(selection);
            return new Canonical(document.representation(),new Decision(decision.status().name(),decision.reasonCode(),decision.stage()),
                    document.coreStatus(),document.feedbackDeliveryAllowed(),text(root.get("canonical"),"pronunciationFeedbackSource"),
                    selection,coaching(root.get("coaching")),read(root.get("score"),Score.class),
                    RunPodContract.RESULT_V6.equals(document.schemaVersion())
                            ? read(root.get("visual"),LipVisual.class) : read(root.get("visual"),AudioVisual.class));
        } catch (Exception ignored) {
            // Never retain or expose rejected JSON/Jackson diagnostics.
            throw new CanonicalAnalysisViewException(CANONICAL_RESULT_UNAVAILABLE);
        }
    }

    private Coaching coaching(JsonNode node) throws java.io.IOException {
        if (node.isNull()) return null;
        String version=text(node,"schemaVersion"),adapter=text(node,"adapterStatus"),generation=text(node,"generationStatus");
        int attempts=node.get("dispatchAttempts").intValue();
        // Copy only branch-approved fields. coreInputValidation and coreSha256 stay private.
        return switch (adapter) {
            case "READY" -> {
                var items=new ArrayList<CoachingItem>();
                for (var item:node.get("items")) {
                    var candidate=read(item.get("candidate"),Candidate.class);
                    checkBrowserNumbers(candidate);
                    items.add(new CoachingItem(candidate,read(item.get("expression"),Expression.class)));
                }
                yield new ReadyCoaching(version,adapter,generation,List.copyOf(items),attempts,
                        node.get("fallbackReason").isNull()?null:text(node,"fallbackReason"),
                        node.get("naturalLanguageSemanticsFullyVerified").booleanValue(),node.get("visualCorrectiveClaimsAllowed").booleanValue(),
                        node.hasNonNull("feedback")?text(node,"feedback"):null);
            }
            case "GLOBAL_REJECT","GLOBAL_INCONCLUSIVE","GLOBAL_SYSTEM_FAILURE","NO_PERMITTED_COACHING_CONTENT" ->
                    new EmptyCoaching(version,adapter,generation,List.of(),attempts);
            case "FAIL_CLOSED" -> new FailedCoaching(version,adapter,generation,List.of(),attempts,text(node,"errorCode"));
            default -> throw new IllegalArgumentException("CANONICAL_PROJECTION_INVALID");
        };
    }

    private <T> T read(JsonNode node,Class<T> type) throws java.io.IOException { return mapper.treeToValue(node,type); }
    private <T> T nullable(JsonNode node,Class<T> type) throws java.io.IOException { return node.isNull()?null:read(node,type); }
    private static String text(JsonNode node,String key) { return node.get(key).textValue(); }
    private static void checkBrowserNumbers(Selection selection) {
        var c=selection.coverage();
        safe(c.evaluatedConsonantPositions());safe(c.visiblePositionCount());safe(c.permittedPatternCount());
        c.presentationStateCounts().values().forEach(CanonicalPublicProjection::safe);
        selection.reviewReasonCounts().values().forEach(CanonicalPublicProjection::safe);
        selection.candidates().forEach(CanonicalPublicProjection::checkBrowserNumbers);
    }
    private static void checkBrowserNumbers(Candidate candidate) {
        safe(candidate.repetitionCount());
        for (var fact:candidate.facts()) {
            safe(fact.expectedIndex());safe(fact.location().wordIndex());
            var l=fact.location();
            for(var seconds:List.of(l.wordStartS(),l.wordEndS(),l.phoneStartS(),l.phoneEndS())) {
                if(seconds.signum()<0 || !Double.isFinite(seconds.doubleValue()))
                    throw new IllegalArgumentException("CANONICAL_PROJECTION_INVALID");
            }
        }
    }
    private static void safe(long value) {
        if(value<0 || value>9007199254740991L)throw new IllegalArgumentException("CANONICAL_PROJECTION_INVALID");
    }
}
