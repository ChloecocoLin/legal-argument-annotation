CREATE TABLE trial_segmentation_records (
 id VARCHAR(36) PRIMARY KEY,
 record_kind VARCHAR(32) NOT NULL,
 case_id VARCHAR(36) NOT NULL,
 actor_id VARCHAR(36) NOT NULL,
 payload_json LONGTEXT NOT NULL
);
CREATE INDEX idx_trial_segmentation_case ON trial_segmentation_records(case_id, record_kind);
