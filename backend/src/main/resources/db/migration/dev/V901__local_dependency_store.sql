-- Local/test fixture store only; never included in prod migrations.
CREATE TABLE local_annotation_dependencies (
    object_kind VARCHAR(64) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    PRIMARY KEY (object_kind,object_id)
);
