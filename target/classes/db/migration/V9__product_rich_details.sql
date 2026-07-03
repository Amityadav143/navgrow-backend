-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V9 — rich product detail fields (so admins can populate the product page)
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE products ADD COLUMN IF NOT EXISTS tagline        TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS summary        TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS warranty       TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_urls     TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS features       TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS benefits       TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS applications   TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS specifications TEXT;
