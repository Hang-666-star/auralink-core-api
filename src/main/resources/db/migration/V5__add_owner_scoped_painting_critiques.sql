-- Independent of Guide and Creation: one explicit, owner-scoped score-free Critic task.
CREATE TABLE painting_critiques (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    public_id TEXT NOT NULL,
    owner_user_id INTEGER NOT NULL,
    painting_id INTEGER NOT NULL,
    image_asset_id INTEGER NOT NULL,
    source_image_sha256 VARCHAR(64) NOT NULL,
    title_snapshot TEXT,
    profile VARCHAR(64) NOT NULL,
    input_fingerprint VARCHAR(64) NOT NULL,
    evaluator_version VARCHAR(64) NOT NULL,
    model_identity VARCHAR(255) NOT NULL,
    prompt_schema_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_json TEXT,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    lease_expires_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_painting_critiques_public_id UNIQUE (public_id),
    CONSTRAINT fk_painting_critiques_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_painting_critiques_painting FOREIGN KEY (painting_id) REFERENCES paintings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_painting_critiques_image FOREIGN KEY (image_asset_id) REFERENCES media_assets(id) ON DELETE RESTRICT
);
CREATE INDEX idx_painting_critiques_owner_painting ON painting_critiques(owner_user_id, painting_id, created_at);
CREATE INDEX idx_painting_critiques_status_created ON painting_critiques(status, created_at);
CREATE INDEX idx_painting_critiques_running_lease ON painting_critiques(status, lease_expires_at);
-- The database is the authoritative duplicate-submission boundary before dispatch.
CREATE UNIQUE INDEX uq_painting_critiques_active_fingerprint
    ON painting_critiques(owner_user_id, painting_id, input_fingerprint)
    WHERE status IN ('QUEUED', 'RUNNING');
