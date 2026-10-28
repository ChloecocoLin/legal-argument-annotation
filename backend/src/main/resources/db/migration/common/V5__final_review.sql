CREATE TABLE final_annotation_versions (
    id VARCHAR(36) PRIMARY KEY,
    task_document_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    UNIQUE (task_document_id, version_number),
    FOREIGN KEY (session_id) REFERENCES adjudication_sessions(id),
    CHECK (version_number > 0)
);
-- Private durable workflow history, independent of the shared AuditEvent proposal.
CREATE TABLE final_review_transitions (
    id VARCHAR(36) PRIMARY KEY,
    final_version_id VARCHAR(36) NOT NULL,
    sequence_number INT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    UNIQUE (final_version_id,sequence_number),
    FOREIGN KEY (final_version_id) REFERENCES final_annotation_versions(id)
);
