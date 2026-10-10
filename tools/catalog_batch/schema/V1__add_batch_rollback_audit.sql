BEGIN;

ALTER TABLE import_batches
    ADD COLUMN IF NOT EXISTS rolled_back_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rolled_back_by VARCHAR(200),
    ADD COLUMN IF NOT EXISTS rollback_reason TEXT;

ALTER TABLE import_batches DROP CONSTRAINT IF EXISTS import_batches_status_check;
ALTER TABLE import_batches
    ADD CONSTRAINT import_batches_status_check
    CHECK (status IN ('pending', 'importing', 'completed', 'completed_with_errors', 'failed', 'rolled_back'));

ALTER TABLE import_runs DROP CONSTRAINT IF EXISTS import_runs_status_check;
ALTER TABLE import_runs
    ADD CONSTRAINT import_runs_status_check
    CHECK (status IN ('running', 'completed', 'completed_with_errors', 'failed', 'rolled_back'));

CREATE TABLE IF NOT EXISTS batch_rollbacks (
    rollback_id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES import_batches(batch_id) ON DELETE RESTRICT,
    batch_code VARCHAR(50) NOT NULL,
    artwork_count INTEGER NOT NULL,
    image_count INTEGER NOT NULL,
    operator_name VARCHAR(200) NOT NULL,
    reason TEXT NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS batch_rollbacks_batch_id_idx ON batch_rollbacks(batch_id);
CREATE INDEX IF NOT EXISTS batch_rollbacks_executed_at_idx ON batch_rollbacks(executed_at);

COMMIT;
