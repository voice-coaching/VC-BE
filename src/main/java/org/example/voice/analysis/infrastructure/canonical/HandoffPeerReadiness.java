package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Audio keeps its exact v5 pins when an additive v2 gateway advertises video too. */
final class HandoffPeerReadiness {
    private HandoffPeerReadiness() {}

    static boolean matches(JsonNode response, Map<String, String> audio, Map<String, String> video) {
        if (response == null || !response.path("executorConfigured").isBoolean()
                || !response.path("executorConfigured").booleanValue()) return false;
        String version = response.path("contractVersion").asText();
        boolean v2 = "voice-coaching.canonical-handoff.v2".equals(version);
        if (!v2 && !"voice-coaching.canonical-handoff.v1".equals(version)) return false;
        JsonNode digests = response.path("schemaDigests");
        if (!digests.isObject() || digests.size() < audio.size()) return false;
        for (var expected : audio.entrySet()) {
            JsonNode value = digests.get(expected.getKey());
            if (value == null || !value.isTextual() || !expected.getValue().equals(value.textValue())) return false;
        }
        var fields = digests.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (audio.containsKey(field.getKey())) continue;
            String expected = v2 ? video.get(field.getKey()) : null;
            if (expected == null || !field.getValue().isTextual()
                    || !expected.equals(field.getValue().textValue())) return false;
        }
        return true;
    }
}
