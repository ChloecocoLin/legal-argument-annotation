-- Confirmed session/item/decision entities; JSON preserves the complete public resource.
CREATE TABLE adjudication_sessions (
    id VARCHAR(36) PRIMARY KEY,
    task_document_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NOT NULL
);
CREATE INDEX idx_adjudication_case ON adjudication_sessions(task_document_id,status);
CREATE TABLE adjudication_items (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    item_kind VARCHAR(20) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES adjudication_sessions(id),
    CHECK (item_kind IN ('PROPOSITION','RELATION'))
);
CREATE TABLE adjudication_decisions (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES adjudication_sessions(id),
    FOREIGN KEY (item_id) REFERENCES adjudication_items(id)
);
-- Private workflow eligibility index. Retained source snapshots are never rewritten.
CREATE TABLE adjudication_returned_versions (
    version_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (version_id) REFERENCES annotation_versions(id),
    FOREIGN KEY (session_id) REFERENCES adjudication_sessions(id)
);
