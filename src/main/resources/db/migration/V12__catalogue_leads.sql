-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V12 — Catalogue download lead capture:
--   Visitors provide name, mobile, email & requirement before downloading the
--   company catalogue. Leads are stored here and surfaced in Admin → Catalogue Leads.
-- ═══════════════════════════════════════════════════════════════════════════

-- Lead lifecycle enum (guard against re-creation on repeat runs)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'lead_status') THEN
        CREATE TYPE lead_status AS ENUM ('NEW','CONTACTED','QUALIFIED','CONVERTED','CLOSED');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS catalogue_leads (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    mobile        VARCHAR(30)  NOT NULL,
    email         VARCHAR(160) NOT NULL,
    requirement   TEXT,
    company       VARCHAR(160),
    city          VARCHAR(120),
    catalogue_key VARCHAR(80),
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(512),
    source        VARCHAR(120),
    status        lead_status  NOT NULL DEFAULT 'NEW',
    assigned_to   VARCHAR(255),
    admin_notes   TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_catalogue_leads_created ON catalogue_leads (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_catalogue_leads_status  ON catalogue_leads (status);
CREATE INDEX IF NOT EXISTS idx_catalogue_leads_email   ON catalogue_leads (email);
