-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V4 — B2B Request-For-Quote (RFQ) workflow
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS rfqs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_number        VARCHAR(40)  NOT NULL UNIQUE,
    buyer_name        VARCHAR(255) NOT NULL,
    buyer_email       VARCHAR(255) NOT NULL,
    buyer_phone       VARCHAR(20)  NOT NULL,
    company           VARCHAR(255),
    gstin             VARCHAR(20),
    delivery_city     VARCHAR(120),
    delivery_state    VARCHAR(120),
    pincode           VARCHAR(12),
    user_id           UUID,
    notes             TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    quoted_subtotal   NUMERIC(12,2),
    quoted_gst        NUMERIC(12,2),
    quoted_shipping   NUMERIC(12,2),
    quoted_total      NUMERIC(12,2),
    quote_valid_until TIMESTAMP,
    admin_message     TEXT,
    assigned_to       VARCHAR(255),
    payment_terms     VARCHAR(120),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    quoted_at         TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rfq_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_id            UUID NOT NULL REFERENCES rfqs(id) ON DELETE CASCADE,
    product_id        UUID,
    product_name      VARCHAR(255) NOT NULL,
    sku               VARCHAR(80),
    quantity          INTEGER NOT NULL,
    specification     TEXT,
    quoted_unit_price NUMERIC(12,2),
    line_total        NUMERIC(12,2),
    gst_rate          INTEGER
);

CREATE INDEX IF NOT EXISTS idx_rfqs_status      ON rfqs(status);
CREATE INDEX IF NOT EXISTS idx_rfqs_buyer_email ON rfqs(buyer_email);
CREATE INDEX IF NOT EXISTS idx_rfqs_user_id     ON rfqs(user_id);
CREATE INDEX IF NOT EXISTS idx_rfq_items_rfq    ON rfq_items(rfq_id);
