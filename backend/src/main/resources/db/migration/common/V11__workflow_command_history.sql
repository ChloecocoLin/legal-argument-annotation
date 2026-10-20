CREATE TABLE workflow_command_history (
 id VARCHAR(36) PRIMARY KEY,
 actor_id VARCHAR(36) NOT NULL,
 operation_key VARCHAR(200) NOT NULL,
 occurred_at VARCHAR(20) NOT NULL,
 payload_json LONGTEXT NOT NULL
);
CREATE INDEX idx_workflow_command_actor ON workflow_command_history(actor_id,occurred_at);
