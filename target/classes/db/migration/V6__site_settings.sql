-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V6 — global site settings (key→JSON store)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS site_settings (
    setting_key   VARCHAR(80) PRIMARY KEY,
    setting_value TEXT,
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO site_settings (setting_key, setting_value)
VALUES ('global', '{}')
ON CONFLICT (setting_key) DO NOTHING;
