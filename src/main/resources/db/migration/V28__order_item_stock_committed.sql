-- ============================================================================
-- V28 — Remember which order lines actually took stock
--
-- An online order takes stock when its payment clears. If another buyer took
-- the last units in between, the paid order is flagged "[STOCK SHORTFALL]" and
-- that line takes nothing. Cancelling such an order used to put the FULL
-- quantity back, inventing stock that never existed (and letting the next
-- buyer pay for it too). order_items.stock_committed records, per line,
-- whether units were really taken, so a cancellation only returns those.
--
-- Additive and safe: every existing line is treated as committed, which is
-- exactly how cancellations behaved before this change.
-- ============================================================================

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS stock_committed BOOLEAN DEFAULT TRUE;
UPDATE order_items SET stock_committed = TRUE WHERE stock_committed IS NULL;
