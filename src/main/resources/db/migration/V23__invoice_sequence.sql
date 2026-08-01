-- GST-compliant sequential invoice numbering.
--
-- GST law requires tax-invoice numbers to be a consecutive, gap-free series
-- (unique per financial year). A dedicated counter table gives us an atomic,
-- restart-safe sequence — the in-process order-number counter is not adequate
-- for a statutory invoice series.
--
-- One row per financial-year series (e.g. '2025-26'); `last_seq` is the last
-- number issued for that series. Assignment uses SELECT ... FOR UPDATE so
-- concurrent orders can never receive the same invoice number.

CREATE TABLE IF NOT EXISTS invoice_sequence (
    fin_year   VARCHAR(9)  PRIMARY KEY,   -- e.g. '2025-26'
    last_seq   BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMP   NOT NULL DEFAULT now()
);

-- Backfill an invoice_date column so the invoice can show the statutory date it
-- was issued (distinct from order creation, though usually the same moment).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS invoice_date TIMESTAMP;
