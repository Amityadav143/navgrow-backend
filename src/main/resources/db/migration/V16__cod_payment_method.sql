-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V16 — Cash on delivery.
--
-- Delivery zones already declare whether COD is available and what it costs,
-- and the storefront tells buyers so, but there was no way to actually choose
-- it and no record of how an order was paid. This adds the payment method and
-- the COD handling charge to the order.
--
-- ONLINE orders are paid before dispatch (Razorpay); COD orders are created
-- CONFIRMED with payment still PENDING and settle on delivery.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_charge     NUMERIC(10,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN orders.payment_method IS 'ONLINE (prepaid via Razorpay) or COD (collected on delivery)';
COMMENT ON COLUMN orders.cod_charge     IS 'Cash-handling fee for the delivery zone, included in grand_total';

CREATE INDEX IF NOT EXISTS idx_orders_payment_method ON orders (payment_method);
