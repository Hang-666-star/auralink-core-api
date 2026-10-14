-- S2 additive extension. V014 and its applied checksum remain unchanged.
-- Explicit operator/test migration only; never applied automatically at startup.
ALTER TABLE assistant_runs ADD COLUMN model_id varchar(120) NOT NULL DEFAULT '';
ALTER TABLE assistant_runs ADD COLUMN options jsonb NOT NULL DEFAULT '{}' CHECK(octet_length(options::text)<=65536);
ALTER TABLE assistant_runs ADD COLUMN lease_owner varchar(120);
ALTER TABLE assistant_runs ADD COLUMN lease_until timestamptz;
ALTER TABLE assistant_runs ADD COLUMN dispatched_at timestamptz;
ALTER TABLE assistant_runs ADD COLUMN retrieval_dispatched_at timestamptz;
ALTER TABLE assistant_runs ADD COLUMN retry_of uuid;
ALTER TABLE assistant_runs ADD COLUMN attempt integer NOT NULL DEFAULT 1 CHECK(attempt BETWEEN 1 AND 100);
ALTER TABLE assistant_runs ADD COLUMN usage jsonb NOT NULL DEFAULT '{}' CHECK(octet_length(usage::text)<=16384);
ALTER TABLE assistant_runs ADD COLUMN retrieval jsonb NOT NULL DEFAULT '{}' CHECK(octet_length(retrieval::text)<=65536);
ALTER TABLE assistant_runs ADD COLUMN context_manifest jsonb NOT NULL DEFAULT '{}' CHECK(octet_length(context_manifest::text)<=65536);
ALTER TABLE assistant_runs ADD COLUMN last_error varchar(160);
ALTER TABLE assistant_runs ADD CONSTRAINT assistant_retry_same_session FOREIGN KEY(session_id,retry_of) REFERENCES assistant_runs(session_id,id);
CREATE INDEX assistant_expired_leases ON assistant_runs(lease_until) WHERE status='EXECUTING';
CREATE TABLE assistant_choice_cards (
 session_id uuid NOT NULL,run_id uuid NOT NULL,source_message_id uuid NOT NULL,
 card_id varchar(80) NOT NULL,title varchar(500) NOT NULL,options jsonb NOT NULL,
 created_at timestamptz NOT NULL,PRIMARY KEY(run_id,card_id),
 CHECK(jsonb_typeof(options)='array' AND jsonb_array_length(options) BETWEEN 2 AND 4),
 CHECK(octet_length(options::text)<=8192),
 FOREIGN KEY(session_id,run_id) REFERENCES assistant_runs(session_id,id),
 FOREIGN KEY(session_id,source_message_id) REFERENCES assistant_messages(session_id,id)
);
-- S1's lifecycle authority remains the deletion entry point. Extend its tombstone
-- erasure atomically, including when an S1-compatible binary deletes the session.
CREATE FUNCTION assistant_erase_execution_on_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='DELETED' AND OLD.status<>'DELETED' THEN
  UPDATE assistant_runs SET options='{}',usage='{}',retrieval='{}',context_manifest='{}',last_error=NULL,lease_owner=NULL,lease_until=NULL WHERE session_id=NEW.id;
  DELETE FROM assistant_choice_cards WHERE session_id=NEW.id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER assistant_execution_tombstone AFTER UPDATE OF status ON assistant_sessions
 FOR EACH ROW EXECUTE FUNCTION assistant_erase_execution_on_delete();
