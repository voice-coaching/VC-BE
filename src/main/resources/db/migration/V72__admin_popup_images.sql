ALTER TABLE admin_popups ADD COLUMN image_revision UUID;
CREATE TABLE admin_popup_images (
    id UUID PRIMARY KEY,
    popup_id BIGINT NOT NULL REFERENCES admin_popups(id),
    object_key VARCHAR(255) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING','ATTACHED','RETIRED','DELETED')),
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_popup_attached_image ON admin_popup_images(popup_id) WHERE state='ATTACHED';
CREATE INDEX idx_popup_image_cleanup ON admin_popup_images(state,created_at);
