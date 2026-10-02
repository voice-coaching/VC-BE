package org.example.voice.analysis.controller.dto;

import java.util.List;

/** Internal capability response; accepted versions describe the installed result reader. */
public record RunPodWorkerReadinessV2ResponseDto(
        String status,
        String contractVersion,
        String serverTime,
        boolean legacyReady,
        List<String> acceptedResultSchemaVersions,
        List<String> analysisProfiles,
        List<SchemaDigestDto> schemaDigests,
        boolean canonicalSupported,
        boolean canonicalAdmissionEnabled,
        String canonicalStatus,
        String evidenceStoreStatus,
        String reasonCode
) {
    public record SchemaDigestDto(String file, String sha256) {
    }
}
