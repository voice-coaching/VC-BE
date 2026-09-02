alter table voice_recordings
    add column visual_object_key varchar(1000),
    add column visual_mime_type varchar(100),
    add column visual_file_size_bytes bigint,
    add column visual_sha256 varchar(64),
    add column visual_consent_receipt_sha256 varchar(64),
    add column visual_consent_policy_revision varchar(100);

alter table voice_recordings
    add constraint chk_voice_recordings_visual_all_or_none check (
        (visual_object_key is null
            and visual_mime_type is null
            and visual_file_size_bytes is null
            and visual_sha256 is null
            and visual_consent_receipt_sha256 is null
            and visual_consent_policy_revision is null)
        or
        (visual_object_key is not null
            and visual_mime_type = 'video/mp4'
            and visual_file_size_bytes > 0
            and length(visual_sha256) = 64
            and length(visual_consent_receipt_sha256) = 64
            and visual_consent_policy_revision is not null)
    );
