CREATE TABLE trial_exports (
 id VARCHAR(36) PRIMARY KEY,
 status VARCHAR(24) NOT NULL,
 payload_json LONGTEXT NOT NULL,
 input_json LONGTEXT NOT NULL
);
CREATE INDEX idx_trial_export_queue ON trial_exports(status,id);
CREATE TABLE trial_files (
 id VARCHAR(36) PRIMARY KEY,
 export_id VARCHAR(36) NULL UNIQUE,
 payload_json LONGTEXT NOT NULL,
 generated_json LONGTEXT NULL,
 FOREIGN KEY(export_id) REFERENCES trial_exports(id)
);
CREATE TABLE trial_data_events (
 id VARCHAR(36) PRIMARY KEY,
 object_id VARCHAR(36) NOT NULL,
 payload_json LONGTEXT NOT NULL
);
CREATE TABLE trial_downloads (
 id VARCHAR(36) PRIMARY KEY,
 file_id VARCHAR(36) NOT NULL,
 token_digest VARCHAR(64) NOT NULL UNIQUE,
 payload_json LONGTEXT NOT NULL,
 FOREIGN KEY(file_id) REFERENCES trial_files(id)
);
CREATE TABLE trial_export_receipts (
 actor_id VARCHAR(36) NOT NULL,
 operation_key VARCHAR(160) NOT NULL,
 idempotency_key VARCHAR(36) NOT NULL,
 request_sha256 VARCHAR(64) NOT NULL,
 encrypted_result LONGTEXT NOT NULL,
 expires_at VARCHAR(20) NOT NULL,
 PRIMARY KEY(actor_id,operation_key,idempotency_key)
);
