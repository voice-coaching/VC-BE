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
    public static final int CONTROL_LIMIT = 65_536;
    public static final int RESULT_LIMIT = 1_048_576;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> documents = new ConcurrentHashMap<>();

    public RunPodContract() {
        mapper.getFactory().setStreamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(RESULT_LIMIT).build());
    }

    public static String timestamp(OffsetDateTime value) {
        return UTC.format(value.withOffsetSameInstant(ZoneOffset.UTC));
    }

    public JsonNode parse(byte[] bytes, String kind) {
        if (bytes.length > (kind.equals("result") ? RESULT_LIMIT : CONTROL_LIMIT)) {
            throw new RunPodContractException(413, "PAYLOAD_TOO_LARGE");
        }
        JsonNode node;
        try {
            String raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            node = mapper.readTree(raw);
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
        if (node == null || !schemas.computeIfAbsent(kind, this::schema).validate(node).isEmpty()) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
        // Python and Java share UTF-16 limits in addition to schema code-point limits.
        utf16(node, definition(kind), root(kind));
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
        String name = kind.equals("result") ? "runpod_result_v1.schema.json" : "runpod_http_control_v1.schema.json";
        return documents.computeIfAbsent(name, this::readDocument);
    }

    private JsonNode readDocument(String name) {
        try (var input = getClass().getResourceAsStream("/contracts/" + name)) {
            if (input == null) throw new IllegalStateException("RunPod contract resource missing");
            return mapper.readTree(input);
        } catch (IOException error) { throw new IllegalStateException("RunPod contract unreadable", error); }
    }

    private JsonNode definition(String kind) { return root(kind).path("$defs").path(kind); }

    private JsonSchema schema(String kind) {
        ObjectNode root = ((ObjectNode) root(kind)).deepCopy();
        root.put("$ref", "#/$defs/" + kind);
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setFormatAssertionsEnabled(true);
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root, config);
    }

    private void utf16(JsonNode value, JsonNode schema, JsonNode root) {
        if (schema.has("$ref")) { utf16(value, root.at(schema.get("$ref").asText().substring(1)), root); return; }
        if (value.isTextual() && schema.has("maxLength") && value.textValue().length() > schema.get("maxLength").asInt()) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
        if (value.isObject()) schema.path("properties").fields().forEachRemaining(e -> {
            if (value.has(e.getKey())) utf16(value.get(e.getKey()), e.getValue(), root);
        });
        if (value.isArray() && schema.path("items").isObject()) {
            for (JsonNode item : value) utf16(item, schema.get("items"), root);
        }
        for (String key : new String[]{"anyOf", "oneOf", "allOf"}) {
            for (JsonNode branch : schema.path(key)) utf16(value, branch, root);
        }
    }
}
