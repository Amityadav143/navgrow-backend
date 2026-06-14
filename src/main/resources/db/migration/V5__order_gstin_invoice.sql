-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V5 — GSTIN + invoice number on orders (for GST-compliant tax invoices)
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE orders ADD COLUMN IF NOT EXISTS gstin          VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_orders_invoice ON orders(invoice_number);
