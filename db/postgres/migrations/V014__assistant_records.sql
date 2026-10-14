-- Explicit, operator-applied additive migration. NOT on Flyway's runtime classpath.
-- V014 is reserved against the verified V001-V013 manifest in this change.
-- No existing rows, tables, credentials, ownership, grants or migrations are modified.
CREATE TABLE assistant_sessions (
 id uuid PRIMARY KEY, owner_id bigint NOT NULL REFERENCES catalog_users(id),
 title varchar(160) NOT NULL, active_branch_id uuid, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 summary_auto_enabled boolean NOT NULL DEFAULT false,
 status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DELETED')),
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, deleted_at timestamptz,
 UNIQUE(owner_id,id),
 CHECK ((status='ACTIVE' AND deleted_at IS NULL) OR (status='DELETED' AND deleted_at IS NOT NULL))
);
CREATE INDEX assistant_sessions_owner ON assistant_sessions(owner_id,status,updated_at DESC,id);
CREATE TABLE assistant_branches (
 id uuid PRIMARY KEY, session_id uuid NOT NULL REFERENCES assistant_sessions(id),
 parent_branch_id uuid, fork_message_id uuid, head_message_id uuid,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0), created_at timestamptz NOT NULL,
 UNIQUE(session_id,id), FOREIGN KEY(session_id,parent_branch_id) REFERENCES assistant_branches(session_id,id)
);
CREATE TABLE assistant_messages (
 id uuid PRIMARY KEY, session_id uuid NOT NULL REFERENCES assistant_sessions(id), parent_message_id uuid,
 version_group_id uuid NOT NULL, supersedes_id uuid,
 role text NOT NULL CHECK(role IN ('USER','ASSISTANT','SYSTEM')),
 body text NOT NULL CHECK(octet_length(body)<=262144),
 state text NOT NULL CHECK(state IN ('ACCEPTED','COMPLETED','FAILED','CANCELLED','INTERRUPTED')),
 run_id uuid, created_at timestamptz NOT NULL,
 UNIQUE(session_id,id),
 FOREIGN KEY(session_id,parent_message_id) REFERENCES assistant_messages(session_id,id),
 FOREIGN KEY(session_id,version_group_id) REFERENCES assistant_messages(session_id,id),
 FOREIGN KEY(session_id,supersedes_id) REFERENCES assistant_messages(session_id,id),
 CHECK(parent_message_id IS DISTINCT FROM id),CHECK(supersedes_id IS DISTINCT FROM id)
);
CREATE INDEX assistant_messages_session ON assistant_messages(session_id,created_at,id);
ALTER TABLE assistant_sessions ADD CONSTRAINT assistant_session_active_branch_fk FOREIGN KEY(id,active_branch_id) REFERENCES assistant_branches(session_id,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE assistant_branches ADD CONSTRAINT assistant_branch_fork_fk FOREIGN KEY(session_id,fork_message_id) REFERENCES assistant_messages(session_id,id);
ALTER TABLE assistant_branches ADD CONSTRAINT assistant_branch_head_fk FOREIGN KEY(session_id,head_message_id) REFERENCES assistant_messages(session_id,id);
CREATE TABLE assistant_runs (
 id uuid PRIMARY KEY, session_id uuid NOT NULL REFERENCES assistant_sessions(id), branch_id uuid NOT NULL,
 input_message_id uuid, kind text NOT NULL CHECK(kind IN ('CHAT','SUMMARY','RETRIEVAL')),
 status text NOT NULL CHECK(status IN ('QUEUED','EXECUTING','COMPLETED','FAILED','CANCELLED','INTERRUPTED')),
 fence bigint NOT NULL DEFAULT 0 CHECK(fence>=0), sequence bigint NOT NULL DEFAULT 0 CHECK(sequence>=0),
 checkpoint text NOT NULL DEFAULT '' CHECK(octet_length(checkpoint)<=262144),
 context_version bigint NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(session_id,id), FOREIGN KEY(session_id,branch_id) REFERENCES assistant_branches(session_id,id),
 FOREIGN KEY(session_id,input_message_id) REFERENCES assistant_messages(session_id,id)
);
-- Pending work does not occupy the single executing slot. Capacity and CHAT precedence
-- are enforced while holding the session row lock, not by a queued+executing unique index.
CREATE UNIQUE INDEX assistant_one_executing ON assistant_runs(session_id) WHERE status='EXECUTING';
CREATE UNIQUE INDEX assistant_one_queued_summary ON assistant_runs(session_id) WHERE status='QUEUED' AND kind='SUMMARY';
CREATE INDEX assistant_run_queue ON assistant_runs(session_id,status,kind,created_at,id);
ALTER TABLE assistant_messages ADD CONSTRAINT assistant_message_run_fk FOREIGN KEY(session_id,run_id) REFERENCES assistant_runs(session_id,id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE assistant_requests (
 owner_id bigint NOT NULL REFERENCES catalog_users(id), operation varchar(40) NOT NULL,
 key_hash char(64) NOT NULL CHECK(key_hash ~ '^[0-9a-f]{64}$'),
 payload_hash char(64) NOT NULL CHECK(payload_hash ~ '^[0-9a-f]{64}$'),
 session_id uuid REFERENCES assistant_sessions(id), result_id uuid,
 status text NOT NULL CHECK(status IN ('ACTIVE','EXPIRED','DELETED')),
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 PRIMARY KEY(owner_id,operation,key_hash), CHECK(expires_at>created_at),
 FOREIGN KEY(owner_id,session_id) REFERENCES assistant_sessions(owner_id,id),
 CHECK(status='ACTIVE' OR result_id IS NULL)
);
-- Minimal owner/operation/key/payload tombstones are retained; TTL expires replayable
-- result references, NEVER makes a used key available for a new paid execution.
CREATE INDEX assistant_requests_expiry ON assistant_requests(status,expires_at);
CREATE TABLE assistant_attachments (
 id uuid PRIMARY KEY, session_id uuid NOT NULL REFERENCES assistant_sessions(id),
 storage_key varchar(80) NOT NULL UNIQUE CHECK(storage_key ~ '^[0-9a-f-]{36}$'),
 filename varchar(180) NOT NULL, mime_type text NOT NULL CHECK(mime_type IN ('image/jpeg','image/png')),
 byte_size bigint NOT NULL CHECK(byte_size>0 AND byte_size<=10485760),
 sha256 char(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
 width integer NOT NULL DEFAULT 0 CHECK(width>=0),height integer NOT NULL DEFAULT 0 CHECK(height>=0),
 status text NOT NULL CHECK(status IN ('UPLOADING','READY','FAILED','DELETING','DELETED')),
 created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 UNIQUE(session_id,id),CHECK(status<>'READY' OR (width>0 AND height>0))
);
CREATE INDEX assistant_attachments_cleanup ON assistant_attachments(status,updated_at);
CREATE TABLE assistant_message_attachments (
 session_id uuid NOT NULL,message_id uuid NOT NULL,attachment_id uuid NOT NULL,position integer NOT NULL CHECK(position>=0),
 PRIMARY KEY(message_id,attachment_id),UNIQUE(message_id,position),
 FOREIGN KEY(session_id,message_id) REFERENCES assistant_messages(session_id,id),
 FOREIGN KEY(session_id,attachment_id) REFERENCES assistant_attachments(session_id,id)
);
CREATE TABLE assistant_run_events (
 session_id uuid NOT NULL,run_id uuid NOT NULL,sequence bigint NOT NULL CHECK(sequence>0),
 event_type varchar(40) NOT NULL,payload jsonb NOT NULL CHECK(octet_length(payload::text)<=262144),created_at timestamptz NOT NULL,
 PRIMARY KEY(run_id,sequence),FOREIGN KEY(session_id,run_id) REFERENCES assistant_runs(session_id,id)
);
CREATE TABLE assistant_citations (
 id uuid PRIMARY KEY,session_id uuid NOT NULL,run_id uuid NOT NULL,ordinal integer NOT NULL CHECK(ordinal>0),
 source_type text NOT NULL CHECK(source_type IN ('PLATFORM_ARTWORK','RAGFLOW_DOCUMENT')),
 source_id text NOT NULL,source_version text NOT NULL,projection_version text NOT NULL,
 provenance jsonb NOT NULL CHECK(octet_length(provenance::text)<=65536),
 excerpt text NOT NULL CHECK(octet_length(excerpt)<=32768),excerpt_sha256 char(64) NOT NULL,
 UNIQUE(run_id,ordinal),FOREIGN KEY(session_id,run_id) REFERENCES assistant_runs(session_id,id)
);
CREATE TABLE assistant_interaction_selections (
 id uuid PRIMARY KEY,session_id uuid NOT NULL,source_message_id uuid NOT NULL,target_message_id uuid NOT NULL UNIQUE,
 card_id text NOT NULL,option_id text NOT NULL,created_at timestamptz NOT NULL,
 FOREIGN KEY(session_id,source_message_id) REFERENCES assistant_messages(session_id,id),
 FOREIGN KEY(session_id,target_message_id) REFERENCES assistant_messages(session_id,id)
);
CREATE TABLE assistant_summaries (
 id uuid PRIMARY KEY,session_id uuid NOT NULL,branch_id uuid NOT NULL,head_message_id uuid NOT NULL,
 context_hash char(64) NOT NULL,model_config_hash char(64) NOT NULL,prompt_version varchar(80) NOT NULL,
 current_run_id uuid NOT NULL,body text NOT NULL DEFAULT '' CHECK(octet_length(body)<=262144),
 UNIQUE(session_id,branch_id,head_message_id,context_hash,model_config_hash,prompt_version),
 FOREIGN KEY(session_id,branch_id) REFERENCES assistant_branches(session_id,id),
 FOREIGN KEY(session_id,head_message_id) REFERENCES assistant_messages(session_id,id),
 FOREIGN KEY(session_id,current_run_id) REFERENCES assistant_runs(session_id,id)
);
CREATE TABLE assistant_stage_classifications (
 session_id uuid NOT NULL,message_id uuid NOT NULL,version integer NOT NULL CHECK(version>0),
 label varchar(80) NOT NULL,source text NOT NULL CHECK(source IN ('RULE','USER')),classifier_version varchar(80) NOT NULL,created_at timestamptz NOT NULL,
 PRIMARY KEY(message_id,version),FOREIGN KEY(session_id,message_id) REFERENCES assistant_messages(session_id,id)
);
