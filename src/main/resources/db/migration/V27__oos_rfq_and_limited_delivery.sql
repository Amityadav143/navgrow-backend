-- ============================================================================
-- V27 — Out-of-stock quotes + per-product limited delivery area
--
-- 1) rfq_items.backorder — marks RFQ lines raised for products that were out of
--    stock when the buyer asked, so the team sees what needs sourcing and quotes
--    a lead time. Set by the server from live stock.
--
-- 2) products.delivery_restricted / allowed_pincodes / delivery_area_note —
--    lets an admin sell a product only within selected pincodes (exact codes,
--    prefixes like 734*, or ranges like 734001-734015). Checkout and order
--    creation enforce it; buyers elsewhere are offered a quote instead.
--
-- All columns are additive with safe defaults: existing products stay
-- deliverable everywhere and existing RFQ lines are not backorders.
-- ============================================================================

ALTER TABLE rfq_items ADD COLUMN IF NOT EXISTS backorder        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rfq_items ADD COLUMN IF NOT EXISTS stock_at_request INTEGER;

ALTER TABLE products ADD COLUMN IF NOT EXISTS delivery_restricted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS allowed_pincodes   TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS delivery_area_note VARCHAR(255);

-- Lets the admin list filter "limited-area" products cheaply.
CREATE INDEX IF NOT EXISTS idx_products_delivery_restricted
    ON products (delivery_restricted) WHERE delivery_restricted = TRUE;
