-- Private durable workflow history, not the pending shared AuditEvent/L01-L03 model.
-- Unlike 24-hour idempotency receipts, reasons and target selections never expire.
CREATE TABLE adjudication_transitions (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES adjudication_sessions(id)
);
