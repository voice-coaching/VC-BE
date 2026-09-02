alter table analysis_results
    add column visual_supplement_schema_version varchar(100),
    add column visual_evidence_relation varchar(40),
    add column visual_approved_claim_id varchar(192),
    add column visual_renderer_key varchar(192),
    add column visual_phone_anchor_ref varchar(64),
    add column visual_supplement_sha256 varchar(64);

alter table analysis_results
    add constraint chk_analysis_results_visual_all_or_none check (
        (visual_supplement_schema_version is null
            and visual_evidence_relation is null
            and visual_approved_claim_id is null
            and visual_renderer_key is null
            and visual_phone_anchor_ref is null
            and visual_supplement_sha256 is null)
        or
        (visual_supplement_schema_version = 'voice-coaching.visual-supplement.v1'
            and visual_evidence_relation = 'supports_upstream'
            and visual_approved_claim_id is not null
            and visual_renderer_key is not null
            and length(visual_phone_anchor_ref) = 64
            and length(visual_supplement_sha256) = 64)
    );
