-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V8 — audit logs (mutating-request traceability)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID PRIMARY KEY,
    method      VARCHAR(10),
    endpoint    VARCHAR(300),
    status_code INTEGER,
    user_email  VARCHAR(160),
    ip_address  VARCHAR(60),
    duration_ms BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_method  ON audit_logs(method);
