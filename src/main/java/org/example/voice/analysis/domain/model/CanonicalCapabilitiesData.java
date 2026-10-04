package org.example.voice.analysis.domain.model;

import java.util.List;
import java.util.Map;

/** Configuration and scope are distinct from a user's permission to submit a job. */
public record CanonicalCapabilitiesData(String capabilityVersion, String analysisProfile,
        List<String> resultSchemas, boolean admissionEnabled, Map<String, Scope> scopes) {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Scope(boolean supported, String reasonCode) {}
}
