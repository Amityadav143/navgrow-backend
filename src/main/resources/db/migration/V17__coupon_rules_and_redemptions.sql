-- ─────────────────────────────────────────────────────────────────────────────
-- V17 · Coupon rules + per-customer redemption ledger
--
-- 1. NAVGROW10 is retuned to the intended welcome promo: a flat-capped 10% that
--    gives at most ₹250 off, and only on orders of ₹3,000 or more. Below ₹3,000
--    it does not apply at all (min_order_amount). Because 10% of ₹3,000 already
--    exceeds ₹250, the cap makes the effective benefit ₹250 for every qualifying
--    order.
-- 2. A redemption ledger enforces "once per customer": one UNIQUE (coupon, user)
--    row is written when an order that used the coupon is confirmed.
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE coupons
   SET min_order_amount = 3000.00,
       max_discount     = 250.00,
       description      = 'Welcome 10% off — up to ₹250 on orders ₹3,000+ (once per customer)'
 WHERE code = 'NAVGROW10';

CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    coupon_id   UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    order_id    UUID,
    coupon_code VARCHAR(50),
    redeemed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_user UNIQUE (coupon_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_user ON coupon_redemptions(user_id);
