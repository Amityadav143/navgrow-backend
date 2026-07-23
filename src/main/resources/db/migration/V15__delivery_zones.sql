-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V15 — Delivery zones, serviceability and charges by pincode.
--
-- Shipping was a single flat rule (free above ₹5,000, otherwise ₹150), which
-- is wrong for a company dispatching from Siliguri: a local delivery and one to
-- the Andamans cost very different amounts and take very different times.
--
-- Zones are matched on pincode PREFIX rather than storing ~19,000 individual
-- pincodes. Indian pincodes are hierarchical — the first digit is the region,
-- the first two the circle, the first three the sorting district — so prefixes
-- express real postal geography and stay maintainable by hand.
--
-- The longest matching prefix wins, so '734' (Siliguri) beats '73' (North
-- Bengal) beats '7' (East region). Priority breaks ties.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS delivery_zones (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(120) NOT NULL,
    -- Comma-separated pincode prefixes, e.g. '734' or '70,71,72,73,74'
    pincode_prefixes  TEXT         NOT NULL,
    serviceable       BOOLEAN      NOT NULL DEFAULT TRUE,
    base_charge       NUMERIC(10,2) NOT NULL DEFAULT 0,
    -- Order value at or above which delivery is free in this zone.
    -- NULL means "never free here" (used for remote zones).
    free_above        NUMERIC(12,2),
    eta_min_days      INTEGER      NOT NULL DEFAULT 3,
    eta_max_days      INTEGER      NOT NULL DEFAULT 7,
    cod_available     BOOLEAN      NOT NULL DEFAULT FALSE,
    cod_charge        NUMERIC(10,2) NOT NULL DEFAULT 0,
    express_available BOOLEAN      NOT NULL DEFAULT FALSE,
    express_charge    NUMERIC(10,2) NOT NULL DEFAULT 0,
    express_eta_days  INTEGER,
    -- Higher priority wins when two prefixes are the same length.
    priority          INTEGER      NOT NULL DEFAULT 0,
    note              VARCHAR(255),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_delivery_zones_active ON delivery_zones (active);

-- Per-order delivery snapshot, so a dispatched order keeps the promise it was
-- sold on even if the zone is re-priced later.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_zone      VARCHAR(120);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_speed     VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_eta_min   INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_eta_max   INTEGER;

-- ── Seed: dispatch origin is Siliguri, West Bengal (734001) ────────────────
INSERT INTO delivery_zones
  (name, pincode_prefixes, serviceable, base_charge, free_above,
   eta_min_days, eta_max_days, cod_available, cod_charge,
   express_available, express_charge, express_eta_days, priority, note)
VALUES
  ('Siliguri Local', '734', TRUE, 0, 0,
   1, 2, TRUE, 0, TRUE, 100, 1, 100,
   'Same-city dispatch — free delivery on every order'),

  ('North Bengal & Sikkim', '735,736,737,733', TRUE, 60, 3000,
   2, 3, TRUE, 40, TRUE, 150, 1, 90,
   'Next-day dispatch from the Siliguri warehouse'),

  ('West Bengal', '70,71,72,73,74', TRUE, 90, 5000,
   2, 4, TRUE, 50, TRUE, 200, 2, 80,
   'Statewide surface delivery'),

  ('Metro Cities', '11,40,56,60,50,38,41,20', TRUE, 120, 5000,
   3, 5, TRUE, 60, TRUE, 250, 2, 70,
   'Delhi, Mumbai, Bengaluru, Chennai, Hyderabad, Ahmedabad, Pune, Ghaziabad'),

  ('North East India', '78,79,795,796,797,798,799', TRUE, 200, 10000,
   5, 9, FALSE, 0, FALSE, 0, NULL, 60,
   'Surface only — hill and border routes add transit time'),

  ('Jammu, Kashmir & Ladakh', '18,19,194', TRUE, 220, 12000,
   6, 10, FALSE, 0, FALSE, 0, NULL, 55,
   'Restricted-route transit; weather delays possible'),

  ('Rest of India', '1,2,3,4,5,6,7,8,9', TRUE, 150, 5000,
   4, 7, TRUE, 60, FALSE, 0, NULL, 10,
   'Standard nationwide surface delivery'),

  ('Andaman & Nicobar', '744', FALSE, 0, NULL,
   0, 0, FALSE, 0, FALSE, 0, NULL, 95,
   'Not currently serviceable — please contact us for a freight quotation'),

  -- Lakshadweep is the single pincode 682555. The wider 682 range is Ernakulam
  -- (Kochi), so a 3-digit prefix here would wrongly block a major serviceable city.
  ('Lakshadweep', '682555', FALSE, 0, NULL,
   0, 0, FALSE, 0, FALSE, 0, NULL, 95,
   'Not currently serviceable — please contact us for a freight quotation')
ON CONFLICT DO NOTHING;
