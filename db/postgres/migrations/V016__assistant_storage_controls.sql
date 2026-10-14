-- Additive only. V001-V015 remain byte-identical. No defaults activate a production retention policy.
ALTER TABLE assistant_attachments ADD COLUMN draft_expires_at timestamptz;
-- Preserve acknowledged S3 draft lifetime, never manufacture orphan uploads as recoverable drafts.
UPDATE assistant_attachments a SET draft_expires_at=r.expires_at
FROM assistant_requests r
WHERE r.operation='UPLOAD_ATTACHMENT' AND r.result_id=a.id AND r.session_id=a.session_id;
CREATE INDEX assistant_attachment_quota ON assistant_attachments(session_id,status) INCLUDE(byte_size);
CREATE INDEX assistant_attachment_cleanup ON assistant_attachments(status,updated_at,id);
CREATE INDEX assistant_attachment_draft_expiry ON assistant_attachments(draft_expires_at,id) WHERE status='READY';
