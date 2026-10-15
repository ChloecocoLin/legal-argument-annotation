-- D-branch trial only. Keep out of prod Flyway locations until shared model review.
CREATE TABLE trial_audit_events (
 id VARCHAR(36) PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL,
 task_document_id VARCHAR(36) NOT NULL,
 object_id VARCHAR(36) NOT NULL,
 occurred_at VARCHAR(20) NOT NULL,
 payload_json LONGTEXT NOT NULL
);
CREATE INDEX idx_trial_audit_object ON trial_audit_events(object_id,occurred_at);
CREATE TABLE trial_final_commitments (
 task_document_id VARCHAR(36) PRIMARY KEY,
 final_version_id VARCHAR(36) NOT NULL UNIQUE,
 audit_event_id VARCHAR(36) NOT NULL UNIQUE,
 evidence_json LONGTEXT NOT NULL,
 FOREIGN KEY(final_version_id) REFERENCES final_annotation_versions(id),
 FOREIGN KEY(audit_event_id) REFERENCES trial_audit_events(id)
);
