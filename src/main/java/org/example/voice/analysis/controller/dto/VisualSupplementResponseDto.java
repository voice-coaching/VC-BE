package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.VisualSupplementData;

public record VisualSupplementResponseDto(
        String schemaVersion,
        Integer selectedExpectedIndex,
        String evidenceRelation,
        String approvedClaimId,
        String rendererKey,
        String upstreamPhoneAnchorRef,
        String supplementSha256
) {
    public static VisualSupplementResponseDto from(VisualSupplementData data) {
        if (data == null) {
            return null;
        }
        return new VisualSupplementResponseDto(
                data.schemaVersion(),
                data.selectedExpectedIndex(),
                data.evidenceRelation(),
                data.approvedClaimId(),
                data.rendererKey(),
                data.upstreamPhoneAnchorRef(),
                data.supplementSha256()
        );
    }
}
