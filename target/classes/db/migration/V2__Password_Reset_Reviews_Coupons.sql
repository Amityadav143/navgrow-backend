-- ═══════════════════════════════════════════════════════════════════════════
-- Navgrow Engineering Backend - V2 Schema
-- Adds: password_reset_tokens, product_reviews, coupons, user profile
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Password Reset Tokens ─────────────────────────────────────────────────
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prt_token   ON password_reset_tokens(token);
CREATE INDEX idx_prt_user    ON password_reset_tokens(user_id);

-- ── Product Reviews ───────────────────────────────────────────────────────
CREATE TABLE product_reviews (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewer_name VARCHAR(255) NOT NULL,
    reviewer_email VARCHAR(255),
    rating      INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title       VARCHAR(255),
    body        TEXT,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reviews_product ON product_reviews(product_id);
CREATE INDEX idx_reviews_approved ON product_reviews(is_approved) WHERE is_approved = TRUE;

-- ── Coupons ───────────────────────────────────────────────────────────────
CREATE TYPE coupon_type AS ENUM ('PERCENTAGE', 'FLAT');

CREATE TABLE coupons (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(50) UNIQUE NOT NULL,
    description      VARCHAR(255),
    coupon_type      coupon_type NOT NULL DEFAULT 'PERCENTAGE',
    value            NUMERIC(8,2) NOT NULL,   -- percent or flat ₹
    min_order_amount NUMERIC(12,2) DEFAULT 0,
    max_discount     NUMERIC(12,2),            -- cap for percentage coupons
    usage_limit      INTEGER,                  -- NULL = unlimited
    usage_count      INTEGER NOT NULL DEFAULT 0,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from       TIMESTAMP NOT NULL DEFAULT NOW(),
    valid_until      TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coupons_code   ON coupons(code);
CREATE INDEX idx_coupons_active ON coupons(is_active);

-- ── Add coupon_code to orders ─────────────────────────────────────────────
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS coupon_discount NUMERIC(12,2) DEFAULT 0;

-- ── Add profile fields to users ───────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS company    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address    TEXT,
    ADD COLUMN IF NOT EXISTS city       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS state      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pincode    VARCHAR(10),
    ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- ── Refresh token store (optional — for invalidation) ────────────────────
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      TEXT UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rt_token   ON refresh_tokens(token);
CREATE INDEX idx_rt_user_id ON refresh_tokens(user_id);

-- ── Seed sample coupons ───────────────────────────────────────────────────
INSERT INTO coupons (code, description, coupon_type, value, min_order_amount, max_discount, usage_limit)
VALUES
  ('NAVGROW10',  'Welcome 10% off',            'PERCENTAGE', 10.00, 500.00,  500.00,  NULL),
  ('FLAT200',    '₹200 off on orders ₹2000+',  'FLAT',      200.00, 2000.00, NULL,    500),
  ('RAILWAY15',  'Railway partners 15% off',   'PERCENTAGE', 15.00, 1000.00, 1000.00, NULL);
