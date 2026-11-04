package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Check peer capability and admission; release hashes are observational metadata. */
final class HandoffPeerReadiness {
    private HandoffPeerReadiness() {}

    static boolean matches(JsonNode response, Map<String, String> expected) {
        if (response == null || !response.path("executorConfigured").isBoolean()
                || !response.path("executorConfigured").booleanValue()) return false;
        if (!CanonicalHandoffReadiness.VERSION.equals(response.path("contractVersion").asText())) return false;
        if (!response.path("admissionEnabled").isBoolean()
                || !response.path("admissionEnabled").booleanValue()) return false;
        JsonNode focuses = response.path("supportedLearningFocuses");
        if (!focuses.isArray() || focuses.size() != 1
                || !"PRONUNCIATION".equals(focuses.get(0).asText())) return false;
        return true;
    }
}
