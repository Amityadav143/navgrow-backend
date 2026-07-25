-- Delivery is chargeable everywhere except Siliguri.
--
-- Zones outside Siliguri previously waived delivery above a spend threshold
-- (₹3,000–₹12,000 depending on zone). Outbound freight is a real cost on every
-- one of those consignments, so the waiver is withdrawn: only the same-city
-- Siliguri zone, which we deliver ourselves, stays free.
--
-- NULL (rather than a very large number) is deliberate — DeliveryService treats
-- NULL as "this zone never offers free delivery", so the checkout stops showing
-- a "spend X more for free delivery" nudge that would never pay off.

UPDATE delivery_zones
   SET free_above = NULL
 WHERE name <> 'Siliguri Local';

-- Siliguri stays free from the first rupee (free_above = 0).
UPDATE delivery_zones
   SET free_above = 0,
       note = 'Same-city dispatch — free delivery on every order'
 WHERE name = 'Siliguri Local';

-- Keep the zone notes honest about the change.
UPDATE delivery_zones
   SET note = COALESCE(NULLIF(note, ''), 'Delivery charged by zone')
 WHERE name <> 'Siliguri Local';
