
-- Audit log table for all API calls
CREATE TABLE IF NOT EXISTS audit_logs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp     TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    method        VARCHAR(10),
    endpoint      VARCHAR(512),
    status_code   INT,
    user_email    VARCHAR(255),
    ip_address    VARCHAR(64),
    duration_ms   BIGINT,
    request_body  TEXT,
    response_summary VARCHAR(1024),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user       ON audit_logs(user_email);
CREATE INDEX IF NOT EXISTS idx_audit_endpoint   ON audit_logs(endpoint);

-- User address table
CREATE TABLE IF NOT EXISTS user_addresses (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label          VARCHAR(100),
    recipient_name VARCHAR(255) NOT NULL,
    phone          VARCHAR(20),
    address_line1  VARCHAR(512) NOT NULL,
    address_line2  VARCHAR(512),
    locality       VARCHAR(255),
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(100) NOT NULL,
    pincode        VARCHAR(12)  NOT NULL,
    country        VARCHAR(100) NOT NULL DEFAULT 'India',
    type           VARCHAR(20)  NOT NULL DEFAULT 'BOTH',
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_addresses_user ON user_addresses(user_id);

-- Company/GST profile table
CREATE TABLE IF NOT EXISTS company_profiles (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company_name        VARCHAR(255),
    gstin               VARCHAR(20),
    pan                 VARCHAR(15),
    business_type       VARCHAR(100),
    website             VARCHAR(512),
    registered_address  TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Add EDITOR role to enum (if not already present) ─────────────────────────
-- PostgreSQL doesn't support IF NOT EXISTS for ALTER TYPE, so we check manually
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'EDITOR'
        AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'user_role')
    ) THEN
        ALTER TYPE user_role ADD VALUE 'EDITOR';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'MANAGER'
        AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'user_role')
    ) THEN
        ALTER TYPE user_role ADD VALUE 'MANAGER';
    END IF;
END $$;

-- ── Extend users table with all fields used by entities + controllers ──────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS company      VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS locality     VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS city         VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS state        VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pincode      VARCHAR(12);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio          TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url   TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- ── Orders: add gstin column for B2B invoices ──────────────────────────────────
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gstin VARCHAR(20);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_orders_razorpay ON orders(razorpay_order_id) WHERE razorpay_order_id IS NOT NULL;
