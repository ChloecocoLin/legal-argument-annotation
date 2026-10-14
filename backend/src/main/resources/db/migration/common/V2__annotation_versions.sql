-- D owns immutable snapshots and workflow metadata. External IDs are validated by the A/B/C port.
CREATE TABLE annotation_case_counters (
    task_document_id VARCHAR(36) PRIMARY KEY,
    last_version_number INT NOT NULL DEFAULT 0,
    CHECK (last_version_number >= 0)
);
CREATE TABLE annotation_versions (
    id VARCHAR(36) PRIMARY KEY,
    task_document_id VARCHAR(36) NOT NULL,
    assignment_id VARCHAR(36) NOT NULL,
    source_draft_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    record_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(0) NOT NULL,
    created_by_id VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP(0) NOT NULL,
    updated_by_id VARCHAR(36) NOT NULL,
    UNIQUE (task_document_id, version_number),
    CHECK (version_number > 0),
    CHECK (record_version > 0),
    CHECK (status IN ('SUBMITTED','WITHDRAWN','UNDER_ADJUDICATION','ADJUDICATED'))
);
CREATE INDEX idx_annotation_versions_assignment ON annotation_versions(task_document_id,assignment_id,status);
CREATE TABLE annotation_version_snapshots (
    version_id VARCHAR(36) PRIMARY KEY,
    snapshot_json LONGTEXT NOT NULL,
    FOREIGN KEY (version_id) REFERENCES annotation_versions(id)
);
-- Private transactional receipts, NOT the pending public AuditEvent model or L01-L03.
CREATE TABLE annotation_mutation_receipts (
    actor_id VARCHAR(36) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    request_sha256 VARCHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    before_json LONGTEXT NOT NULL,
    after_json LONGTEXT NOT NULL,
    note_text LONGTEXT NOT NULL,
    created_at TIMESTAMP(0) NOT NULL,
    expires_at TIMESTAMP(0) NOT NULL,
    PRIMARY KEY (actor_id,operation_key,idempotency_key)
);
