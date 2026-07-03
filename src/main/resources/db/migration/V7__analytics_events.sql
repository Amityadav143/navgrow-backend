-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V7 — analytics events (lightweight, privacy-respecting funnel tracking)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS analytics_events (
    id          UUID PRIMARY KEY,
    event_name  VARCHAR(60)  NOT NULL,
    label       VARCHAR(200),
    value       DOUBLE PRECISION,
    session_id  VARCHAR(80),
    path        VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ae_event   ON analytics_events(event_name);
CREATE INDEX IF NOT EXISTS idx_ae_created ON analytics_events(created_at);
CREATE INDEX IF NOT EXISTS idx_ae_session ON analytics_events(session_id);
