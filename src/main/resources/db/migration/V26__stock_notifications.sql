-- ============================================================================
-- V26 — Back-in-stock notifications ("notify me when available")
--
-- Captures a visitor's email on an out-of-stock product so the lead isn't lost,
-- and lets the admin see pent-up demand per product and notify everyone on
-- restock. One active request per email+product (unique constraint).
-- ============================================================================

CREATE TABLE IF NOT EXISTS stock_notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    product_slug  VARCHAR(255) NOT NULL,
    product_name  VARCHAR(255),
    product_id    UUID,
    notified      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    notified_at   TIMESTAMP,
    CONSTRAINT uq_stock_notif_email_slug UNIQUE (email, product_slug)
);

CREATE INDEX IF NOT EXISTS idx_stock_notif_slug_pending
    ON stock_notifications (product_slug) WHERE notified = FALSE;
