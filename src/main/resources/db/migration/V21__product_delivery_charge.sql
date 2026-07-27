-- Per-product delivery charge (per unit), set by the admin.
--
-- Products differ in weight and bulk, so a single flat zone charge is unfair —
-- a heavy pump should cost more to ship than a pack of gloves. This column lets
-- the admin set a per-unit base charge for each product. NULL means "fall back to
-- the delivery zone's default charge". The per-quantity slab discount is applied
-- on top of whichever base is used.

ALTER TABLE products ADD COLUMN IF NOT EXISTS delivery_charge NUMERIC(10,2);
