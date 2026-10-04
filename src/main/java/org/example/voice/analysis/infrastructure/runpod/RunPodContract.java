package org.example.voice.analysis.infrastructure.runpod;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The same checked-in schemas used by the Python worker, isolated from public API JSON settings. */
@Component
public class RunPodContract {
    public static final String VERSION = "voice-coaching.runpod-http.v1.1";
    public static final String CAPABILITY_VERSION = "voice-coaching.runpod-http.v1.2";
    public static final String REQUEST_V1 = "voice-coaching.runpod-analysis-request.v1";
    public static final String REQUEST_V2 = "voice-coaching.runpod-analysis-request.v2";
    public static final String REQUEST_V3 = "voice-coaching.runpod-analysis-request.v3";
    public static final String RESULT_V5 = "voice-coaching.runpod-analysis-result.v5";
    public static final String HANDOFF_PROFILE = "CANONICAL_HANDOFF_20261004_V5";
    public static final String RESULT_V4 = "voice-coaching.runpod-analysis-result.v4";
    private static final String REQUEST_V2_KIND = "analysisRequestV2";
    private static final String RESULT_V4_KIND = "canonicalResultV4";
    public static final int CONTROL_LIMIT = 65_536;
    public static final int RESULT_LIMIT = 1_048_576;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> documents = new ConcurrentHashMap<>();
    private final ObjectMapper canonicalMapper;

    public RunPodContract() {
        mapper.getFactory().setStreamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(2*RESULT_LIMIT).build());
        canonicalMapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public static String timestamp(OffsetDateTime value) {
        return UTC.format(value.withOffsetSameInstant(ZoneOffset.UTC));
    }

    public JsonNode parse(byte[] bytes, String kind) {
        if (bytes.length > (kind.equals("handoff") ? 2*RESULT_LIMIT : kind.equals("result") ? RESULT_LIMIT : CONTROL_LIMIT)) {
            throw new RunPodContractException(413, "PAYLOAD_TOO_LARGE");
        }
        JsonNode node;
        try {
            String raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            node = "handoff".equals(kind) ? canonicalMapper.readTree(raw) : mapper.readTree(raw);
            // Frozen MFA seconds must not round-trip through binary floating point.
            // The legacy mapper and legacy wire semantics remain unchanged.
            if ("result".equals(kind) && node != null
                    && java.util.Set.of(RESULT_V4,RESULT_V5).contains(node.path("schemaVersion").asText())) {
                node = canonicalMapper.readTree(raw);
            }
            // Parse with Jackson's nesting limits before recursive canonicalization.
            // Canonicalizer also rejects lone surrogates and non-finite numbers.
            new JsonCanonicalizer(raw);
        } catch (IOException | IllegalArgumentException error) {
            throw new RunPodContractException(400, "INVALID_JSON");
        }
        validate(node, kind);
        return node;
    }

    public void validate(JsonNode node, String kind) {
        kind = schemaKind(node, kind);
        if (node == null || !schemas.computeIfAbsent(kind, this::schema).validate(node).isEmpty()) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
        // Python and Java share UTF-16 limits in addition to schema code-point limits.
        utf16(node, definition(kind), root(kind));
    }

    private String schemaKind(JsonNode node, String kind) {
        if ("result".equals(kind) && node != null && RESULT_V5.equals(node.path("schemaVersion").asText())) return "canonicalResultV5";
        if ("result".equals(kind) && node != null
                && RESULT_V4.equals(node.path("schemaVersion").asText())) return RESULT_V4_KIND;
        if (!"analysisRequest".equals(kind)) return kind;
        String version = node == null ? "" : node.path("schemaVersion").asText();
        return switch (version) {
            case REQUEST_V1 -> "analysisRequest";
            case REQUEST_V2 -> REQUEST_V2_KIND;
            case REQUEST_V3 -> "analysisRequestV3";
            default -> throw new RunPodContractException(422, "VALIDATION_FAILED");
        };
    }

    /** SHA-256 of the packaged schema bytes, distinct from the JCS payload digest. */
    public String schemaSha256(String filename) {
        try (var input = getClass().getResourceAsStream("/contracts/" + filename)) {
            if (input == null) throw new IllegalStateException("RunPod contract resource missing");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        } catch (IOException | java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("RunPod contract digest unavailable", error);
        }
    }

    public <T> T convert(JsonNode node, Class<T> type) {
        try { return mapper.treeToValue(node, type); }
        catch (IOException | IllegalArgumentException error) { throw new RunPodContractException(422, "VALIDATION_FAILED"); }
    }

    public String encode(Object value, String kind) {
        try {
            String raw = mapper.writeValueAsString(value);
            JsonNode node = parse(raw.getBytes(StandardCharsets.UTF_8), kind);
            return mapper.writeValueAsString(node);
        } catch (IOException error) { throw new IllegalStateException("protocol encoding failed", error); }
    }

    public String digest(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(new JsonCanonicalizer(json).getEncodedUTF8()));
        } catch (Exception error) { throw new RunPodContractException(422, "VALIDATION_FAILED"); }
    }

    public String digest(JsonNode node) { return digest(node.toString()); }

    private JsonNode root(String kind) {
        String name = switch (kind) {
            case "journalPrepare", "journalReference", "journalMetadata", "journalSnapshot", "journalAck" -> "runpod_canonical_journal_v1.schema.json";
            case "evidenceManifest", "evidenceReceipt", "evidenceError", "workerReadinessV2",
                    "canonicalExecutorReadiness" -> "runpod_http_control_v1_2.schema.json";
            case "result" -> "runpod_result_v1.schema.json";
            case "handoff", "handoffMetadata", "archiveReconciliation" -> "runpod_canonical_handoff_v1.schema.json";
            case "canonicalResultV5" -> "runpod_result_v5.schema.json";
            case "analysisRequestV3" -> "runpod_analysis_request_v3.schema.json";
            case RESULT_V4_KIND -> "runpod_result_v4.schema.json";
            case REQUEST_V2_KIND -> "runpod_analysis_request_v2.schema.json";
            default -> "runpod_http_control_v1.schema.json";
        };
        return documents.computeIfAbsent(name, this::readDocument);
    }

    private JsonNode readDocument(String name) {
        try (var input = getClass().getResourceAsStream("/contracts/" + name)) {
            if (input == null) throw new IllegalStateException("RunPod contract resource missing");
            return mapper.readTree(input);
        } catch (IOException error) { throw new IllegalStateException("RunPod contract unreadable", error); }
    }

    private JsonNode definition(String kind) {
        return (REQUEST_V2_KIND.equals(kind) || "analysisRequestV3".equals(kind)) ? root(kind)
                : root(kind).path("$defs").path((RESULT_V4_KIND.equals(kind) || "canonicalResultV5".equals(kind)) ? "result" : kind);
    }

    private JsonSchema schema(String kind) {
        ObjectNode root = ((ObjectNode) root(kind)).deepCopy();
        // v2 is a standalone schema: retain its own root and local reference scope.
        if (!(REQUEST_V2_KIND.equals(kind) || "analysisRequestV3".equals(kind))) root.put("$ref", "#/$defs/" + ((RESULT_V4_KIND.equals(kind) || "canonicalResultV5".equals(kind)) ? "result" : kind));
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setFormatAssertionsEnabled(true);
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root, config);
    }

    private void utf16(JsonNode value, JsonNode schema, JsonNode root) {
        // A frozen embedded $id starts a new local-reference resource (inputValidation).
        JsonNode resource = schema.has("$id") ? schema : root;
        if (schema.has("$ref")) { utf16(value, resource.at(schema.get("$ref").asText().substring(1)), resource); return; }
        if (value.isTextual() && schema.has("maxLength") && value.textValue().length() > schema.get("maxLength").asInt()) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
        if (value.isObject()) schema.path("properties").fields().forEachRemaining(e -> {
            if (value.has(e.getKey())) utf16(value.get(e.getKey()), e.getValue(), resource);
        });
        if (value.isArray() && schema.path("items").isObject()) {
            for (JsonNode item : value) utf16(item, schema.get("items"), resource);
        }
        for (String key : new String[]{"anyOf", "oneOf", "allOf"}) {
            for (JsonNode branch : schema.path(key)) utf16(value, branch, resource);
        }
    }
}
