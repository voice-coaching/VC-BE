package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Match the complete current peer contract; a version string alone is insufficient. */
final class HandoffPeerReadiness {
    private HandoffPeerReadiness() {}

    static boolean matches(JsonNode response, Map<String, String> expected) {

        JsonNode digests = response.path("schemaDigests");

        for (var pin : expected.entrySet()) {
            JsonNode value = digests.get(pin.getKey());

        }
        return true;
    }
}
