-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V13 — HSN/SAC codes for GST compliance.
--
-- Indian GST law requires a tax invoice to carry the HSN code (goods) or SAC
-- code (services) for every line item, and the applicable rate follows from
-- that classification. Products previously carried only a gst_rate with no
-- code backing it.
--
-- hsn_code is also snapshotted onto order_items so a historical invoice keeps
-- the classification that applied at the time of sale, even if the product is
-- later reclassified.
--
-- NOTE: the seeded codes below are sensible defaults for this catalogue and
-- MUST be reviewed by a qualified tax advisor before production billing.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE products    ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(12);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(12);

COMMENT ON COLUMN products.hsn_code    IS 'HSN (goods) / SAC (services) code driving the GST rate';
COMMENT ON COLUMN order_items.hsn_code IS 'HSN/SAC snapshot at time of sale — invoices must not shift retrospectively';

CREATE INDEX IF NOT EXISTS idx_products_hsn ON products (hsn_code);

-- ── Backfill by product type ───────────────────────────────────────────────
-- Head protection, face shields (safety headgear)
UPDATE products SET hsn_code = '6506', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%helmet%' OR name ILIKE '%face shield%');

-- Respirators / breathing appliances
UPDATE products SET hsn_code = '9020', gst_rate = 12.00
 WHERE hsn_code IS NULL AND (name ILIKE '%respirator%' OR name ILIKE '%breathing%');

-- Safety footwear
UPDATE products SET hsn_code = '6403', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%boot%' OR name ILIKE '%footwear%' OR name ILIKE '%shoe%');

-- Protective garments: hi-vis vests, coveralls
UPDATE products SET hsn_code = '6211', gst_rate = 12.00
 WHERE hsn_code IS NULL AND (name ILIKE '%vest%' OR name ILIKE '%coverall%' OR name ILIKE '%jacket%');

-- Gloves and sleeve protectors
UPDATE products SET hsn_code = '6116', gst_rate = 12.00
 WHERE hsn_code IS NULL AND (name ILIKE '%glove%' OR name ILIKE '%sleeve%');

-- Other made-up protective articles (knee pads etc.)
UPDATE products SET hsn_code = '6307', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%knee pad%' OR name ILIKE '%pad set%');

-- Hand tools: wrenches, spanners
UPDATE products SET hsn_code = '8204', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%wrench%' OR name ILIKE '%spanner%');

-- Fasteners: bolts, screws, nuts
UPDATE products SET hsn_code = '7318', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%bolt%' OR name ILIKE '%screw%' OR name ILIKE '%nut %');

-- Hydraulic lifting / handling machinery
UPDATE products SET hsn_code = '8425', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%hydraulic%' OR name ILIKE '%squeezer%' OR name ILIKE '%stretcher%');

-- Thermometers / pyrometers
UPDATE products SET hsn_code = '9025', gst_rate = 18.00
 WHERE hsn_code IS NULL AND name ILIKE '%thermometer%';

-- Dimensional measuring instruments: gauges, callipers
UPDATE products SET hsn_code = '9017', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%gauge%' OR name ILIKE '%calliper%' OR name ILIKE '%caliper%');

-- Measuring / checking instruments: flaw detectors, analysers, loggers
UPDATE products SET hsn_code = '9031', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%detector%' OR name ILIKE '%analyser%'
                             OR name ILIKE '%analyzer%' OR name ILIKE '%logger%');

-- Lubricating preparations: greases, penetrant sprays
UPDATE products SET hsn_code = '3403', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%lubricant%' OR name ILIKE '%grease%' OR name ILIKE '%penetrant%');

-- Cleaning / surface-active preparations: solvents
UPDATE products SET hsn_code = '3402', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (name ILIKE '%solvent%' OR name ILIKE '%cleaner%' OR name ILIKE '%cleaning%');

-- ── Category-level fallbacks for anything still unclassified ───────────────
UPDATE products SET hsn_code = '9031', gst_rate = 18.00
 WHERE hsn_code IS NULL AND category ILIKE '%testing%';

UPDATE products SET hsn_code = '8205', gst_rate = 18.00
 WHERE hsn_code IS NULL AND category ILIKE '%railway%';

UPDATE products SET hsn_code = '6307', gst_rate = 18.00
 WHERE hsn_code IS NULL AND (category ILIKE '%ppe%' OR category ILIKE '%safety%');

UPDATE products SET hsn_code = '3403', gst_rate = 18.00
 WHERE hsn_code IS NULL AND category ILIKE '%maintenance%';

-- Final catch-all so no product ships without a code.
UPDATE products SET hsn_code = '8205', gst_rate = COALESCE(gst_rate, 18.00)
 WHERE hsn_code IS NULL;
