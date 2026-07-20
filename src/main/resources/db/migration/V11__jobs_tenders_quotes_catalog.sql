-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V11 — Admin feature fixes:
--   · job_listings: salary range, openings, application deadline (admin form fields)
--   · tenders: apply link, organization, location, category, image, document
--   · quote_requests: industry, city, urgency (calculator context for admin)
--   · catalog_items: admin-managed categories & services
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Job listings: fields the admin form already collects ─────────────────────
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS salary_from          NUMERIC(12,2);
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS salary_to            NUMERIC(12,2);
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS openings             INT NOT NULL DEFAULT 1;
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS application_deadline TIMESTAMP;

-- ── Tenders: richer public info + direct apply link ─────────────────────────
ALTER TABLE tenders ADD COLUMN IF NOT EXISTS apply_link   TEXT;
ALTER TABLE tenders ADD COLUMN IF NOT EXISTS organization VARCHAR(255);
ALTER TABLE tenders ADD COLUMN IF NOT EXISTS location     VARCHAR(255);
ALTER TABLE tenders ADD COLUMN IF NOT EXISTS category     VARCHAR(100);
ALTER TABLE tenders ADD COLUMN IF NOT EXISTS image_url    TEXT;
ALTER TABLE tenders ADD COLUMN IF NOT EXISTS document_url TEXT;

-- ── Quote requests: extra context captured by the calculator ────────────────
ALTER TABLE quote_requests ADD COLUMN IF NOT EXISTS industry VARCHAR(100);
ALTER TABLE quote_requests ADD COLUMN IF NOT EXISTS city     VARCHAR(100);
ALTER TABLE quote_requests ADD COLUMN IF NOT EXISTS urgency  VARCHAR(30);

-- ── Catalog items: admin-managed categories & services ──────────────────────
CREATE TABLE IF NOT EXISTS catalog_items (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_type   VARCHAR(40)  NOT NULL,          -- PRODUCT_CATEGORY | SERVICE | GALLERY_CATEGORY | NEWS_CATEGORY
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(160) NOT NULL,
    description TEXT,
    icon        VARCHAR(60),
    image_url   TEXT,
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_catalog_type_slug UNIQUE (item_type, slug)
);
CREATE INDEX IF NOT EXISTS idx_catalog_type_active ON catalog_items(item_type, is_active, sort_order);
