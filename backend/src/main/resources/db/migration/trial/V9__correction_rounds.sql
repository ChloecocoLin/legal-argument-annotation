CREATE TABLE trial_correction_rounds (
 id VARCHAR(36) PRIMARY KEY,
 parent_final_version_id VARCHAR(36) NOT NULL UNIQUE,
 session_id VARCHAR(36) NOT NULL UNIQUE,
 payload_json LONGTEXT NOT NULL,
 FOREIGN KEY(parent_final_version_id) REFERENCES final_annotation_versions(id),
 FOREIGN KEY(session_id) REFERENCES adjudication_sessions(id)
);
