-- ═══════════════════════════════════════════════════════════════════════════
-- Navgrow Engineering Backend - Initial Database Schema
-- Version: V1  |  Engine: PostgreSQL 15+
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Extensions ────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For ILIKE search performance

-- ── Enum types ────────────────────────────────────────────────────────────
CREATE TYPE user_role      AS ENUM ('ADMIN','MANAGER','USER');
CREATE TYPE order_status   AS ENUM ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','REFUNDED');
CREATE TYPE payment_status AS ENUM ('PENDING','PAID','FAILED','REFUNDED');
CREATE TYPE quote_status   AS ENUM ('NEW','REVIEWING','QUOTED','ACCEPTED','REJECTED','CLOSED');
CREATE TYPE job_status     AS ENUM ('OPEN','CLOSED','PAUSED');
CREATE TYPE app_status     AS ENUM ('NEW','SHORTLISTED','INTERVIEW','OFFERED','REJECTED');
CREATE TYPE news_status    AS ENUM ('DRAFT','PUBLISHED','ARCHIVED');
CREATE TYPE tender_status  AS ENUM ('OPEN','CLOSED','AWARDED','CANCELLED');

-- ── Users / Auth ──────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    role          user_role NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);

-- ── Products ──────────────────────────────────────────────────────────────
CREATE TABLE products (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku           VARCHAR(100) UNIQUE NOT NULL,
    name          VARCHAR(500) NOT NULL,
    slug          VARCHAR(500) UNIQUE NOT NULL,
    category      VARCHAR(100) NOT NULL,
    description   TEXT,
    price         NUMERIC(12,2) NOT NULL,
    mrp           NUMERIC(12,2),
    gst_rate      NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    stock_qty     INTEGER NOT NULL DEFAULT 0,
    min_order_qty INTEGER NOT NULL DEFAULT 1,
    badge         VARCHAR(100),
    image_url     TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured   BOOLEAN NOT NULL DEFAULT FALSE,
    rating        NUMERIC(3,2) DEFAULT 0,
    review_count  INTEGER DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_products_category  ON products(category);
CREATE INDEX idx_products_active    ON products(is_active);
CREATE INDEX idx_products_name_trgm ON products USING gin(name gin_trgm_ops);

-- ── Orders ────────────────────────────────────────────────────────────────
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number        VARCHAR(50) UNIQUE NOT NULL,
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Contact info (denormalized for guest orders)
    customer_name       VARCHAR(255) NOT NULL,
    customer_email      VARCHAR(255) NOT NULL,
    customer_phone      VARCHAR(20)  NOT NULL,
    company_name        VARCHAR(255),
    -- Shipping address
    address_line1       TEXT NOT NULL,
    address_line2       TEXT,
    city                VARCHAR(100) NOT NULL,
    state               VARCHAR(100) NOT NULL,
    pincode             VARCHAR(10)  NOT NULL,
    -- Financials
    subtotal            NUMERIC(12,2) NOT NULL,
    gst_amount          NUMERIC(12,2) NOT NULL DEFAULT 0,
    shipping_charge     NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(12,2) NOT NULL,
    -- Status
    status              order_status NOT NULL DEFAULT 'PENDING',
    payment_status      payment_status NOT NULL DEFAULT 'PENDING',
    -- Razorpay
    razorpay_order_id   VARCHAR(255),
    razorpay_payment_id VARCHAR(255),
    razorpay_signature  VARCHAR(512),
    -- Tracking
    tracking_number     VARCHAR(255),
    courier_name        VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_email   ON orders(customer_email);
CREATE INDEX idx_orders_status  ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at DESC);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name VARCHAR(500) NOT NULL,
    unit_price  NUMERIC(12,2) NOT NULL,
    gst_rate    NUMERIC(5,2) NOT NULL,
    quantity    INTEGER NOT NULL,
    subtotal    NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- ── Contact Messages ──────────────────────────────────────────────────────
CREATE TABLE contact_messages (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    company     VARCHAR(255),
    subject     VARCHAR(500) NOT NULL,
    message     TEXT NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    replied_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_contact_email  ON contact_messages(email);
CREATE INDEX idx_contact_unread ON contact_messages(is_read) WHERE is_read = FALSE;

-- ── Quote Requests ────────────────────────────────────────────────────────
CREATE TABLE quote_requests (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    company       VARCHAR(255),
    service_type  VARCHAR(100) NOT NULL,
    scope         VARCHAR(100),
    duration      VARCHAR(100),
    addons        TEXT[],
    est_low       NUMERIC(12,2),
    est_high      NUMERIC(12,2),
    notes         TEXT,
    status        quote_status NOT NULL DEFAULT 'NEW',
    quoted_amount NUMERIC(12,2),
    assigned_to   VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_quotes_status ON quote_requests(status);
CREATE INDEX idx_quotes_email  ON quote_requests(email);

-- ── Newsletter ────────────────────────────────────────────────────────────
CREATE TABLE newsletter_subscribers (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    name          VARCHAR(255),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    subscribed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    unsubscribed_at TIMESTAMP
);

-- ── Projects ──────────────────────────────────────────────────────────────
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title       VARCHAR(500) NOT NULL,
    slug        VARCHAR(500) UNIQUE NOT NULL,
    category    VARCHAR(100) NOT NULL,
    client      VARCHAR(255),
    location    VARCHAR(255),
    year        VARCHAR(20),
    description TEXT,
    image_url   TEXT,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── News / Blog ───────────────────────────────────────────────────────────
CREATE TABLE news_articles (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title        VARCHAR(500) NOT NULL,
    slug         VARCHAR(500) UNIQUE NOT NULL,
    excerpt      TEXT,
    content      TEXT NOT NULL,
    category     VARCHAR(100),
    image_url    TEXT,
    author_name  VARCHAR(255) NOT NULL DEFAULT 'Navgrow Team',
    status       news_status NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP,
    view_count   INTEGER NOT NULL DEFAULT 0,
    tags         TEXT[],
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_news_status ON news_articles(status);
CREATE INDEX idx_news_published ON news_articles(published_at DESC) WHERE status = 'PUBLISHED';

-- ── Gallery ───────────────────────────────────────────────────────────────
CREATE TABLE gallery_items (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title       VARCHAR(500) NOT NULL,
    category    VARCHAR(100) NOT NULL DEFAULT 'Projects',
    location    VARCHAR(255),
    year        VARCHAR(20),
    image_url   TEXT NOT NULL,
    alt_text    TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Tenders ───────────────────────────────────────────────────────────────
CREATE TABLE tenders (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ref_number   VARCHAR(100) UNIQUE NOT NULL,
    title        VARCHAR(500) NOT NULL,
    description  TEXT,
    value_min    NUMERIC(12,2),
    value_max    NUMERIC(12,2),
    deadline     DATE NOT NULL,
    status       tender_status NOT NULL DEFAULT 'OPEN',
    is_featured  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Job Listings ──────────────────────────────────────────────────────────
CREATE TABLE job_listings (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title        VARCHAR(255) NOT NULL,
    department   VARCHAR(100) NOT NULL,
    job_type     VARCHAR(50) NOT NULL DEFAULT 'Full-time',
    location     VARCHAR(255) NOT NULL,
    experience   VARCHAR(100),
    description  TEXT NOT NULL,
    skills       TEXT[],
    status       job_status NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE job_applications (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id        UUID REFERENCES job_listings(id) ON DELETE SET NULL,
    job_title     VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    experience    VARCHAR(100),
    cover_note    TEXT,
    resume_url    TEXT,
    status        app_status NOT NULL DEFAULT 'NEW',
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_applications_job    ON job_applications(job_id);
CREATE INDEX idx_applications_email  ON job_applications(email);
CREATE INDEX idx_applications_status ON job_applications(status);

-- ── Site Analytics ────────────────────────────────────────────────────────
CREATE TABLE page_views (
    id          BIGSERIAL PRIMARY KEY,
    page_path   VARCHAR(500) NOT NULL,
    referrer    VARCHAR(500),
    ip_address  INET,
    user_agent  TEXT,
    viewed_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pageviews_path ON page_views(page_path);
CREATE INDEX idx_pageviews_date ON page_views(viewed_at DESC);

-- ── Updated_at trigger ────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DO $$ DECLARE t TEXT; BEGIN
    FOREACH t IN ARRAY ARRAY['users','products','orders','quote_requests','projects','news_articles','tenders','job_listings','job_applications'] LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION update_updated_at()', t, t);
    END LOOP;
END $$;

-- ── Default admin user (change password immediately!) ─────────────────────
INSERT INTO users (email, password_hash, full_name, role)
VALUES ('admin@navgrow.org', '$2a$12$rJHdD5hVY7xBkxMkJmITLuPZxhAnk73xO7wKPvlGLxkjTfW7VN4a6', 'Admin', 'ADMIN');
-- Default password: Admin@123 (BCrypt encoded) — CHANGE IMMEDIATELY
