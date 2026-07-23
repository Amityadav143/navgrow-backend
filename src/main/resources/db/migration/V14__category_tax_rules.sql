-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V14 — Category-level HSN/GST defaults, editable by an administrator.
--
-- GST varies by what is being sold, so the rate cannot be a single constant.
-- V13 classified each product individually; this table lets an admin set the
-- default HSN/SAC and rate for a whole category, which new or bulk-imported
-- products inherit when no product-level code is supplied.
--
-- Product-level values always win — this is a fallback, not an override.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS category_tax_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category    VARCHAR(120) NOT NULL UNIQUE,
    hsn_code    VARCHAR(12),
    gst_rate    NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cat_tax_rules_category ON category_tax_rules (category);

-- Seed from the live catalogue's categories. Rates mirror the per-product
-- classification applied in V13 so the two cannot drift on day one.
INSERT INTO category_tax_rules (category, hsn_code, gst_rate, description) VALUES
  ('Safety Equipment',     '6506', 18.00, 'Safety headgear, face shields and protective articles'),
  ('PPE & Workwear',       '6211', 12.00, 'Protective garments; gloves and sleeves fall under 6116'),
  ('Railway Tools',        '8205', 18.00, 'Hand tools and railway track tooling'),
  ('Testing & Inspection', '9031', 18.00, 'Measuring, checking and testing instruments'),
  ('Maintenance Supplies', '3403', 18.00, 'Lubricating and cleaning preparations')
ON CONFLICT (category) DO NOTHING;
