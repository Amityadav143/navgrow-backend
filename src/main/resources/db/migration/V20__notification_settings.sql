-- Admin-configurable notification settings (single row, id = 1).
--
-- Lets the office repoint each kind of notification (careers, orders, quotes,
-- contact) from the admin panel without a redeploy, and flip email/SMS on or off.
-- Credentials are left NULL so they keep coming from environment variables unless
-- an admin deliberately enters them here.

CREATE TABLE IF NOT EXISTS notification_settings (
    id                  INTEGER PRIMARY KEY,
    careers_email       VARCHAR(160),
    orders_email        VARCHAR(160),
    quotes_email        VARCHAR(160),
    contact_email       VARCHAR(160),
    support_email       VARCHAR(160),
    from_email          VARCHAR(160),
    email_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    sms_provider        VARCHAR(20),
    sms_sender_id       VARCHAR(20),
    msg91_auth_key      VARCHAR(200),
    msg91_template_id   VARCHAR(100),
    twilio_account_sid  VARCHAR(120),
    twilio_auth_token   VARCHAR(120),
    twilio_from_number  VARCHAR(30),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed the single row with sensible defaults. These are starting points the
-- admin can change; blank credential fields mean "use environment config".
INSERT INTO notification_settings (
    id, careers_email, orders_email, quotes_email, contact_email, support_email, from_email
) VALUES (
    1,
    'careers@navgrow.org',
    'orders@navgrow.org',
    'quotes@navgrow.org',
    'info@navgrow.org',
    'info@navgrow.org',
    'info@navgrow.org'
)
ON CONFLICT (id) DO NOTHING;
