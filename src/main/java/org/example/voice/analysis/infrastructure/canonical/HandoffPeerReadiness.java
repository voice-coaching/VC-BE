package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Match the complete current peer contract; a version string alone is insufficient. */
final class HandoffPeerReadiness {
    private HandoffPeerReadiness() {}

    static boolean matches(JsonNode response, Map<String, String> expected) {
        if (response == null || !response.path("executorConfigured").isBoolean()
                || !response.path("executorConfigured").booleanValue()) return false;
        if (!CanonicalHandoffReadiness.VERSION.equals(response.path("contractVersion").asText())) return false;
        JsonNode digests = response.path("schemaDigests");
        if (!digests.isObject() || digests.size() != expected.size()) return false;
        for (var pin : expected.entrySet()) {
            JsonNode value = digests.get(pin.getKey());
            if (value == null || !value.isTextual() || !pin.getValue().equals(value.textValue())) return false;
        }
        return true;
    }
}
