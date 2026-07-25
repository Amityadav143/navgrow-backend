-- Seeds the shop catalogue.
--
-- WHY: the products table shipped empty, so /products and /products/featured
-- returned nothing. The homepage therefore fell back to its placeholder list
-- ("dummy products") and product pages had no stock figure to enforce, which is
-- why the quantity cap could not bite. Seeding the real catalogue fixes both at
-- the source.
--
-- Idempotent: ON CONFLICT (sku) DO NOTHING, so re-running never duplicates and
-- never overwrites prices an admin has since edited.

INSERT INTO products (
    sku, name, slug, category, description,
    price, mrp, gst_rate, stock_qty, min_order_qty,
    badge, image_url, is_active, is_featured,
    rating, review_count, hsn_code,
    tagline, summary, warranty, image_urls,
    features, benefits, applications, specifications
) VALUES
('NGP-SAF-001', 'Industrial Safety Helmet (ISI Marked)', 'industrial-safety-helmet-isi', 'Safety Equipment', 'The Navgrow Industrial Safety Helmet is engineered for the demanding conditions of Indian railway maintenance yards and construction sites. Its polycarbonate-reinforced HDPE shell absorbs and disperses impact energy, while the 6-point harness distributes force evenly across the skull — preventing concussion and penetration injuries. UV-stabilised material means the shell retains impact strength even after years of outdoor exposure. Compliant with BIS IS 2925:1984 Type I, this helmet meets all Indian Railways and RDSO procurement requirements.', 480, 650, 18, 120, 1, 'Bestseller', 'https://images.unsplash.com/photo-1582139329536-e7284fece509?w=800&q=80', TRUE, TRUE, 4.8, 24, '6506', 'Your First Line of Defence Against Head Injuries', 'BIS/ISI-certified Type I Class A safety helmet designed for railway, construction, and heavy industrial environments. Lightweight yet impact-resistant with industry-leading ventilation.', '12 months manufacturer warranty against material and workmanship defects. BIS certification number visible on inner ring. Helmet lifespan: 3 years from date of manufacture (check inner stamp).', 'https://images.unsplash.com/photo-1582139329536-e7284fece509?w=800&q=80,https://images.unsplash.com/photo-1504307651254-35680f356dfd?w=800&q=80', 'IS 2925 (BIS/ISI) certified — mandatory for all Indian Railways sites
High-density polyethylene (HDPE) outer shell with UV stabilisation
6-point adjustable suspension harness for all head sizes (52–64 cm)
Ventilated design — 6 air slots reduce heat build-up by 30%
Meets shock absorption standard: 50J impact at 3 m/s
Optional slot for face shields, ear defenders, and visors', 'Mandatory BIS certification — passes all Indian Railways tender requirements without additional testing
Wider brim than standard helmets provides 18% more sun coverage — critical on outdoor railway sites
Harness replacement system — replace damaged harness without buying a new helmet
Compatible with Navgrow Arc-Flash Face Shield and Ear Defender accessories', 'Indian Railways loco shed and track maintenance
Construction and civil engineering sites
Steel plants, foundries, and heavy manufacturing
Mining and quarrying operations
Electrical work and arc flash environments (with visor)', 'Standard: IS 2925:1984 (BIS Certified)
Shell Material: High-Density Polyethylene (HDPE)
Weight: 370 g (±20 g)
Size Range: 52–64 cm (adjustable)
Colour Options: White, Yellow, Blue, Red, Orange
Temperature Range: –20°C to +50°C
Impact Absorption: 50J at 3 m/s (EN 397 equivalent)
Ventilation: 6 integrated air vents'),
('NGP-SAF-002', 'High-Visibility Safety Vest (Class 2)', 'high-visibility-safety-vest', 'Safety Equipment', 'Designed for railway track workers and site supervisors operating near moving vehicles and machinery. The 360° retro-reflective system ensures visibility from all angles in both daylight and low-light. The breathable mesh construction is essential for workers in West Bengal''s heat and humidity — standard polyester vests cause dangerous heat stress. Colour choice: fluorescent yellow (day use) or fluorescent orange (construction zones).', 320, 420, 12, 120, 1, NULL, 'https://images.unsplash.com/photo-1504307651254-35680f356dfd?w=800&q=80', TRUE, FALSE, 4.7, 18, '6211', 'Be Seen. Stay Safe. Meet Railway Standards.', 'EN ISO 20471 Class 2 high-visibility vest with 360° retro-reflective strips. Engineered for track workers, signalmen, and site supervisors who need maximum visibility day and night.', '6 months warranty. Retroreflective performance guaranteed for 50 wash cycles. Replace when reflective strips show visible wear or fluorescent material fades significantly.', 'https://images.unsplash.com/photo-1504307651254-35680f356dfd?w=800&q=80', 'EN ISO 20471:2013 Class 2 certified — 0.5 m² fluorescent material
2 bands of 50 mm retro-reflective tape for night visibility up to 300 m
Breathable open-mesh polyester fabric — reduces heat stress by 40%
Adjustable velcro side fasteners — fits S to 3XL over work garments
Two chest pockets with velcro closure for ID cards and documents
Fade-resistant fluorescent yellow/orange dye — maintains visibility for 50+ washes', 'Meets Indian Railways and RDSO Class 2 minimum visibility standard
Open mesh prevents heat stress — critical for outdoor railway work in India
Velcro adjustment ensures correct fit over winter PPE or summer uniform', 'Railway track maintenance and inspection teams
Flagmen and signalmen at unmanned crossings
Road and civil construction workers
Warehouse and logistics personnel
Airport ground crew', 'Standard: EN ISO 20471:2013 Class 2
Fluorescent Material: ≥0.5 m² (Class 2 minimum)
Retroreflective Tape: 2 × 50 mm bands (≥0.13 m²)
Fabric: 100% Polyester Mesh, 120 gsm
Sizes: S / M / L / XL / XXL / 3XL
Colours: Fluorescent Yellow, Fluorescent Orange
Closure: Velcro side adjusters
Wash Durability: 50 wash cycles at 40°C'),
('NGP-SAF-003', 'Anti-Impact Safety Gloves (Cut Level D)', 'anti-impact-safety-gloves', 'Safety Equipment', 'Developed for railway maintenance technicians who handle rail fasteners, bolt sets, and cutting tools daily. The HPPE cut-level D liner prevents laceration from rail clips and sharp metal edges, while TPR armour absorbs hammer strikes and falling components. The PU-coated palm maintains grip on tools even with oil and grease contamination — a critical safety factor when tightening fish bolts at height or in confined loco shed spaces.', 650, 850, 12, 120, 1, 'Top Rated', 'https://images.unsplash.com/photo-1567361808960-dec9cb578182?w=800&q=80', TRUE, FALSE, 4.9, 31, '6116', 'Cut-Proof. Grip-Enhanced. Impact-Absorbing.', 'EN388 Cut Level D anti-impact gloves for heavy engineering and railway maintenance. TPR impact-absorbing knuckle armour protects against struck-by and caught-between injuries — the #1 cause of hand injuries on rail sites.', '3 months warranty against manufacturing defects. Replace when cut liner shows visible threads or palm coating peels. Do not use for electrical live-line work.', 'https://images.unsplash.com/photo-1567361808960-dec9cb578182?w=800&q=80', 'EN388:2016 Cut Level D — resists sharp metal edges and bolt threads
HPPE (High-Performance Polyethylene) fibre liner — 10× stronger than leather
TPR (Thermoplastic Rubber) dorsal impact armour — absorbs up to 40J
Polyurethane palm coating — superior grip on oily and wet surfaces
Pre-curved ergonomic fit — reduces hand fatigue during tool use
Extended 80 mm cuff with velcro closure — protects wrist gap', 'Rated #1 by Navgrow field technicians — used daily on Siliguri DLS projects
Single glove covers Cut, Impact, and Abrasion hazards — replaces 3 separate gloves
PU palm maintains tactile sensitivity — you can still feel bolt torque feedback', 'Rail fastener installation and removal (fish bolts, spring clips)
Loco maintenance — engine compartment and undercarriage work
Steel fabrication and angle iron handling
Glass and sharp material handling
Electrical panel work (non-insulating — use separate dielectric gloves for live work)', 'Cut Standard: EN388:2016 Level D (HPPE)
Impact Standard: EN 13594 Level 1 (TPR)
Palm Coating: Microporous Polyurethane (PU)
Liner: HPPE + Spandex (4-way stretch)
Sizes: S (7) / M (8) / L (9) / XL (10) / XXL (11)
Cuff Length: 80 mm extended
Abrasion Resistance: EN388 Level 4
Tear Resistance: EN388 Level 4'),
('NGP-SAF-004', 'Steel-Toe Safety Boots (S3 Rated)', 'steel-toe-safety-boots', 'Safety Equipment', 'EN ISO 20345:2011 S3 rated safety boots with steel toe cap, steel midsole, and oil-resistant sole. For railway and construction site daily wear.', 2400, 3200, 18, 40, 1, NULL, 'https://images.unsplash.com/photo-1638803040283-7a5ffd48dad5?w=800&q=80', TRUE, FALSE, 4.6, 15, '6403', 'S3 Protection. All-Day Comfort.', 'EN ISO 20345:2011 S3 rated safety boots with steel toe cap, steel midsole, and oil-resistant sole. For railway and construction site daily wear.', '6 months warranty against manufacturing defects.', 'https://images.unsplash.com/photo-1638803040283-7a5ffd48dad5?w=800&q=80', 'Steel toe cap — 200J impact resistance
Steel anti-penetration midsole (15kN)
Anti-static and oil-resistant rubber sole
EN ISO 20345:2011 S3 certified
Genuine leather upper — durable and waterproof
Energy-absorbing heel cushion', 'Steel midsole prevents puncture from rail spikes and nails
Anti-static prevents static build-up near fuel and explosives', 'Railway maintenance sites
Construction and civil works
Warehousing and logistics', 'Standard: EN ISO 20345:2011 S3
Toe Cap: Steel (200J)
Upper: Genuine split leather
Sizes: 38–48'),
('NGP-SAF-005', 'Full-Face Respirator (P100 Filter)', 'full-face-respirator', 'Safety Equipment', 'Full-face silicon respirator with replaceable P100 filter cartridges. Protects against particulates, fumes, vapours, and gases. Ideal for confined space entry and painting operations.', 1850, 2400, 12, 8, 1, NULL, 'https://images.unsplash.com/photo-1584744982491-665216d95f8b?w=800&q=80', TRUE, FALSE, 4.7, 12, '9020', 'Total Respiratory Protection.', 'Full-face silicon respirator with replaceable P100 filter cartridges. Protects against particulates, fumes, vapours, and gases. Ideal for confined space entry and painting operations.', '12 months. Replace cartridges after 40 hours or if odour detected.', 'https://images.unsplash.com/photo-1584744982491-665216d95f8b?w=800&q=80', 'P100 filter — 99.97% filtration efficiency
Full-face silicon seal — zero leakage under pressure
Wide panoramic visor for full peripheral vision
Replaceable cartridges — P100, organic vapour, acid gas
Low breathing resistance (30 Pa at 95 l/min)
Anti-fog visor coating', 'Full face coverage — eliminates eye and face exposure simultaneously', 'Confined space entry in loco pits
Spray painting and surface treatment
Chemical handling', 'Filter Class: P100 (99.97%)
Face Seal: Medical-grade silicon
Standard: EN 136:1998 (Full Face)'),
('NGP-RLT-006', 'Digital Torque Wrench (10–200 Nm)', 'digital-torque-wrench', 'Railway Tools', 'Critical fastener integrity is the foundation of safe railway operation. Over-tightening fish bolts causes rail joint cracking; under-tightening causes fatigue failure. This digital torque wrench eliminates both risks with ±2% precision and clear confirmation feedback — essential when working at height or in poor lighting conditions in loco sheds. Pre-programme your target torque values (e.g., 250 Nm for M24 fish bolts, 80 Nm for fishplate bolts) and the wrench alerts you the moment the target is reached — no guesswork.', 4800, 6000, 18, 40, 1, 'Professional', 'https://images.unsplash.com/photo-1530124566582-a618bc2615dc?w=800&q=80', TRUE, TRUE, 4.9, 9, '8204', 'RDSO-Grade Precision. Every Bolt. Every Time.', 'Professional-grade digital torque wrench for railway track fastener tightening. ±2% accuracy, 10-value memory, LED+buzzer confirmation. Eliminates over/under-tightening — the primary cause of fish bolt failure on Indian Railways.', '12 months warranty. Recalibrate every 12 months or 5,000 cycles (calibration service available at Navgrow). Traceable calibration certificate included.', 'https://images.unsplash.com/photo-1530124566582-a618bc2615dc?w=800&q=80', '±2% clockwise accuracy — exceeds RDSO specification requirement of ±4%
Range: 10–200 Nm — covers all standard railway fastener torque specifications
360° swivel head — reach fasteners in confined loco shed spaces
LED bar + buzzer confirmation — audible and visual alert at target torque
10-value onboard memory — store torque presets for different bolt types
Hardened chrome-vanadium steel shaft — calibration stable for 5,000+ cycles', 'Ships with factory calibration certificate — no additional lab calibration required for use
Memory presets eliminate reconfiguration between bolt types on long track inspection runs
Angle measurement mode for torque-angle procedures on critical fasteners', 'Fish bolt tightening on rail joints (Indian broad gauge)
Loco bogie bolt torquing in maintenance overhauls
Bridge and viaduct structural fastener checks
General industrial maintenance wherever torque specification applies', 'Torque Range: 10–200 Nm (clockwise)
Accuracy: ±2% CW / ±4% CCW
Drive Size: 1/2" square drive
Display: Backlit LCD with LED bar indicator
Power: 2 × AA batteries (included)
Memory: 10 preset torque values
Material: Chrome-vanadium steel (shaft + ratchet)
Calibration: Factory calibrated with certificate'),
('NGP-RLT-007', 'Rail Track Gauge (Standard 1676 mm)', 'rail-track-gauge', 'Railway Tools', 'Steel track gauge calibrated for Indian broad gauge (1676 mm). Graduated scale with digital callout option. RDSO pattern compliant.', 3200, 4000, 18, 40, 1, NULL, 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80', TRUE, FALSE, 4.8, 7, '9017', 'Precision Gauge Checking. Every Kilometre.', 'Steel track gauge calibrated for Indian broad gauge (1676 mm). Graduated scale with digital callout option. RDSO pattern compliant.', '12 months. Annual calibration recommended.', 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80', 'Factory calibrated to Indian broad gauge 1676 mm
Graduated scale: 1676 ±10 mm readable
Spring-loaded contact feet for consistent pressure
RDSO pattern compliant
Hardened steel construction
Carry case included', 'RDSO-compliant design accepted for all Indian Railways inspection records', 'Routine track geometry inspection
Post-maintenance gauge verification
New track laying acceptance', 'Standard Gauge: 1676 mm (Indian BG)
Range: 1666–1686 mm
Resolution: 0.5 mm
Material: Hardened EN8 steel'),
('NGP-RLT-008', 'Hydraulic Rail Squeezer & Stretcher', 'hydraulic-rail-squeezer', 'Railway Tools', 'Compact hydraulic tool for rail thermal gap adjustment. 120 kN squeeze/stretch force, 700 bar working pressure. Prevents buckling in summer and gap-opening in winter.', 12500, 15000, 18, 6, 1, 'Heavy Duty', 'https://images.unsplash.com/photo-1504328345606-18bbc8c9d7d1?w=800&q=80', TRUE, FALSE, 4.7, 5, '8425', 'Thermal Gap Control. Safe. Fast. Precise.', 'Compact hydraulic tool for rail thermal gap adjustment. 120 kN squeeze/stretch force, 700 bar working pressure. Prevents buckling in summer and gap-opening in winter.', '12 months. Hydraulic seal kit available separately.', 'https://images.unsplash.com/photo-1504328345606-18bbc8c9d7d1?w=800&q=80', '120 kN (12-tonne) force capacity
700 bar working pressure
Dual function: squeeze and stretch
Compact body fits all BG rail profiles
Manual hand pump with pressure gauge
Safety pressure relief valve', 'Prevents thermal buckling — the primary cause of summer derailments in India', 'Rail thermal gap adjustment (CWR)
Fish-plated track gap adjustment
Rail pulling during re-laying', 'Force: 120 kN (squeeze & stretch)
Working Pressure: 700 bar
Cylinder Stroke: 150 mm
Weight: 8.5 kg (unit only)'),
('NGP-RLT-009', 'Ultrasonic Rail Flaw Detector (RDSO Compliant)', 'ultrasonic-rail-flaw-detector', 'Railway Tools', 'Rail internal defects — particularly horizontal cracks in the rail head — are invisible to visual inspection but cause catastrophic failures. This RDSO-compliant UT device uses high-frequency ultrasound to locate internal flaws up to 150 mm below the rail surface. The A-scan waveform identifies defect type; B-scan provides a cross-sectional profile for sizing. Store up to 2,000 inspection records with GPS waypoints and export to IRIMS (Indian Railways Inspection Management System) via USB. Includes 2 probes: 0° longitudinal and 70° transverse.', 28000, 35000, 18, 3, 1, 'Precision', 'https://images.unsplash.com/photo-1581092580497-e0d23cbdf1dc?w=800&q=80', TRUE, FALSE, 4.9, 4, '9031', 'Find Hidden Cracks Before They Find You.', 'Portable phased-array ultrasonic tester for detecting internal rail defects — cracks, delaminations, and inclusions — before they cause derailments. Colour TFT A/B-scan display, USB data export, RDSO-compliant probe set.', '18 months warranty on unit and probes. Annual calibration service offered by Navgrow. Software updates included for lifetime.', 'https://images.unsplash.com/photo-1581092580497-e0d23cbdf1dc?w=800&q=80', 'A-scan and B-scan display modes — visualise defect depth and profile
Detection sensitivity: cracks ≥1 mm depth at 45° incidence
Frequency range: 1–15 MHz — covers all rail steel grades
Colour 5" TFT touchscreen — readable in bright outdoor sunlight
USB + Bluetooth data export — sync with inspection management software
IP65 weatherproof housing — operates in monsoon conditions', 'RDSO specification compliant — accepted for Indian Railways track inspection reports
Bluetooth sync with inspection software eliminates manual data transcription errors
Included calibration block eliminates separate purchase for probe verification', 'Routine track inspection — annual and post-monsoon flaw detection
Rail joint and weld inspection at bolted fish plate and thermit welds
Used rail acceptance testing before installation
Bridge and culvert bearing inspection', 'Test Method: Pulse-echo & through-transmission
Frequency: 1–15 MHz (selectable)
Display: 5" TFT 800×480, outdoor sunlight-readable
Gain Range: 0–110 dB in 0.1 dB steps
Battery Life: 10 hours continuous (Li-ion, rechargeable)
Storage: 2,000 inspection records with waveforms
Connectivity: USB-C, Bluetooth 5.0
Protection: IP65, drop-rated to 1.2 m
Probe Set: 0° + 70° probes, calibration block'),
('NGP-MNT-010', 'Rail Lubricant Grease (20 kg Drum)', 'rail-lubricant-grease', 'Maintenance Supplies', 'NLGI Grade 2 lithium complex grease for rail joint lubrication and axle bearing maintenance. Temperature range –20°C to +180°C. Excellent water resistance for monsoon conditions.', 2200, 2800, 18, 40, 1, NULL, 'https://images.unsplash.com/photo-1601924287811-e34de5d17476?w=800&q=80', TRUE, FALSE, 4.6, 22, '3403', 'NLGI 2 Grade. High Temp. Long Life.', 'NLGI Grade 2 lithium complex grease for rail joint lubrication and axle bearing maintenance. Temperature range –20°C to +180°C. Excellent water resistance for monsoon conditions.', '2-year shelf life (sealed drum). 1 year open.', 'https://images.unsplash.com/photo-1601924287811-e34de5d17476?w=800&q=80', 'NLGI Grade 2 — standard for rail maintenance
Temperature range: –20°C to +180°C
Lithium complex thickener — superior load capacity
Water resistant — tested in monsoon conditions
Anti-oxidant additives — 2× longer service life
20 kg drum with bung for pump dispensing', 'Lithium complex formula outlasts standard lithium grease 2:1 in high-load rail applications', 'Rail joint and fish plate contact surfaces
Axle box and bearing lubrication
Brake rigging pivot points
Coupling gear lubrication', 'Grade: NLGI 2
Base Oil: Mineral (ISO VG 220)
Thickener: Lithium Complex
Temp Range: –20°C to +180°C
Pack Size: 20 kg drum'),
('NGP-MNT-011', 'Anti-Corrosion Penetrant Spray (500 ml)', 'anti-corrosion-penetrant-spray', 'Maintenance Supplies', 'Railway maintenance teams lose hours every week fighting seized fish bolts, corroded fish plates, and rusted track fasteners. This fast-acting penetrant uses capillary action to reach deep into threaded interfaces — breaking the rust-to-metal bond in 30 seconds. Unlike WD-40-type products, the dry film residue does not attract dirt or grit — critical in the railway environment where contamination causes premature wear. Monthly application to fish bolt threads reduces seizure by 90% and extends maintenance intervals.', 380, 499, 18, 120, 1, 'Bestseller', 'https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=800&q=80', TRUE, TRUE, 4.8, 45, '3403', 'Penetrates in 30 Seconds. Protects for 12 Months.', 'Industrial-grade fast-penetrating spray that frees seized bolts, protects against rust, and displaces moisture. Formulated for the corrosive conditions of Indian railway environments — effective even on fish bolts seized for years.', 'Satisfaction guaranteed. Defective products (no spray, no penetration) replaced free. Store in cool, dry location away from heat sources.', 'https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=800&q=80', 'Penetrates seized threads in 30 seconds — tested on 5-year seized fish bolts
Leaves dry protective film — no sticky residue that traps dirt and grit
Moisture displacement formula — safe to use on live electrical equipment
Non-conductive (dielectric) coating — EN 50191 compliant for electrical use
Temperature stable –30°C to +200°C — suitable for engine compartments
500 ml with long precision nozzle — reaches deep into rail fastenings', 'Used on Navgrow''s own projects — standard issue for all site teams
Biodegradable formula — safe for environmentally sensitive railway corridor zones
Non-flammable aerosol (CO₂ propellant) — safe for use near locomotive fuel systems', 'Fish bolt thread lubrication and rust prevention
Track fastener (Pandrol clip, elastic rail clip) rust removal
Locomotive undercarriage nut and bolt maintenance
Brake rigging pivot and clevis pin lubrication
Signal equipment and junction box waterproofing', 'Volume: 500 ml aerosol can
Propellant: CO₂ (non-flammable)
Active Ingredient: Zinc-based rust inhibitor complex
Dielectric Strength: >35 kV/mm (EN 50191)
Flash Point: >61°C
Temperature Range: –30°C to +200°C
Residue: Dry protective film (no sticky residue)
Shelf Life: 3 years from manufacture date'),
('NGP-MNT-012', 'Industrial Cleaning Solvent (5 L)', 'industrial-cleaning-solvent', 'Maintenance Supplies', 'Heavy-duty biodegradable degreaser for engine parts, loco undercarriage, and track machinery. Removes carbon deposits, hydraulic oil, and railway grease without residue.', 950, 1200, 18, 120, 1, NULL, 'https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=800&q=80', TRUE, FALSE, 4.5, 16, '3402', 'Heavy Grease. Gone in Seconds.', 'Heavy-duty biodegradable degreaser for engine parts, loco undercarriage, and track machinery. Removes carbon deposits, hydraulic oil, and railway grease without residue.', '3-year shelf life. SDS (Safety Data Sheet) included.', 'https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=800&q=80', 'Removes engine oil, carbon deposits, and railway grease
Biodegradable — BS EN ISO 14593 compliant
No residue — parts are machining-ready after cleaning
Safe on aluminium, steel, and painted surfaces
Flash point >61°C — safer than petroleum solvents
5L container with measuring cap', 'Biodegradable formula — safe for use in ecologically sensitive railway corridor zones', 'Engine parts cleaning before overhaul
Loco undercarriage degreasing
Workshop floor and machinery cleaning', 'Volume: 5 L
Flash Point: >61°C
pH: 9.5–10.5 (alkaline)
Biodegradable: Yes (BS EN ISO 14593)'),
('NGP-MNT-013', 'Rail Joint Fish Bolt Set (M24×180 mm)', 'rail-fish-bolt-set', 'Maintenance Supplies', 'Forged mild steel fish bolts for standard Indian Railways broad gauge rail joints. Set of 8 bolts with hex nuts and spring washers. IS 3063 specification, hot-dip galvanised.', 1200, 1500, 18, 120, 1, NULL, 'https://images.unsplash.com/photo-1590736969955-71cc94901144?w=800&q=80', TRUE, FALSE, 4.7, 11, '7318', 'IS 3063 Grade. Galvanised. Ready to Install.', 'Forged mild steel fish bolts for standard Indian Railways broad gauge rail joints. Set of 8 bolts with hex nuts and spring washers. IS 3063 specification, hot-dip galvanised.', '10 years corrosion warranty (hot-dip galvanising). Dimensional guarantee to IS 3063.', 'https://images.unsplash.com/photo-1590736969955-71cc94901144?w=800&q=80', 'IS 3063 specification — accepted by all Indian Railways divisions
Forged MS Grade 8.8 — exceeds working load by 2.5×
Hot-dip galvanised — 10+ year corrosion life
Complete set: 8 bolts + 8 hex nuts + 8 spring washers
M24 × 180 mm — standard BG joint bolt size
Thread pitch: 3.0 mm (coarse) for easy fitment', '10+ year corrosion life eliminates the annual inspection maintenance burden of plain steel bolts', 'Rail joint (fish plate) bolt replacement
New broad gauge track construction
Maintenance of existing bolted rail joints', 'Standard: IS 3063
Size: M24 × 180 mm
Grade: 8.8 forged MS
Coating: Hot-dip galvanised 75µm
Set Contains: 8 bolts + 8 nuts + 8 washers'),
('NGP-TST-014', 'Digital Vernier Calliper (0–300 mm)', 'digital-vernier-caliper', 'Testing & Inspection', 'Accurate dimensional measurement is non-negotiable in railway component inspection. Wheel flange height, tyre thickness, axle diameter, and fish plate hole diameter all have RDSO-specified tolerances — a 0.1 mm error can mean a component rejection or, worse, a safety incident. The 0.01 mm digital resolution of this calliper is mandatory for wheel wear measurement (minimum detectable: 0.05 mm). SPC data output connects to quality management software for automatic recording during acceptance inspection.', 1650, 2100, 18, 120, 1, 'Top Rated', 'https://images.unsplash.com/photo-1611791484670-ce19b801d192?w=800&q=80', TRUE, TRUE, 4.8, 38, '9017', 'Measure Once. Machine Right.', 'Professional 0–300 mm digital vernier calliper in hardened stainless steel. 0.01 mm resolution, IP54 splash-proof, auto power-off. The go-to measurement instrument for railway wheel, axle, and component inspection.', '12 months warranty. Factory calibration traceable to NABL. Replacement jaws available. Do not drop — recalibrate after any accidental impact.', 'https://images.unsplash.com/photo-1611791484670-ce19b801d192?w=800&q=80', '0.01 mm (10 µm) resolution — 4× more precise than standard Vernier scale
Hardened stainless steel jaws — maintains accuracy after 100,000+ measurements
IP54 rating — protected from coolant splash and workshop dust
Large backlit LCD with data output port (SPC/RS-232)
Inch/metric switchable with single button press
Auto power-off after 5 minutes — battery life up to 2 years', 'SPC output port allows direct recording to quality systems — eliminates transcription errors
Inch/metric switching avoids confusion when working with imported components
300 mm range covers all standard railway wheel and axle measurement tasks', 'Railway wheel flange height and tyre thickness measurement
Fish plate and joint bar hole diameter inspection
Axle journal diameter measurement in wheel shop
General engineering measurement and machining quality control
Incoming inspection of purchased components', 'Measuring Range: 0–300 mm (0–12")
Resolution: 0.01 mm / 0.0005"
Accuracy: ±0.02 mm (0–150 mm)
Material: Grade 201 Hardened Stainless Steel
Display: Backlit 8-digit LCD
Protection: IP54 (splash & dust resistant)
Power: CR2032 (included), ~2 year life
Data Output: SPC / RS-232 mini port'),
('NGP-TST-015', 'Infrared Thermometer (–50 to 1000°C)', 'infrared-thermometer', 'Testing & Inspection', 'Non-contact infrared thermometer with laser targeting. Range –50 to 1000°C. For bearing hot-box detection, brake drum temperature, and electrical hotspot identification.', 3200, 4200, 18, 40, 1, NULL, 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800&q=80', TRUE, FALSE, 4.7, 19, '9025', 'Non-Contact. Instant. Safe.', 'Non-contact infrared thermometer with laser targeting. Range –50 to 1000°C. For bearing hot-box detection, brake drum temperature, and electrical hotspot identification.', '12 months.', 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800&q=80', '–50 to 1000°C range — covers all railway diagnostic needs
Laser targeting — pinpoints measurement location precisely
D:S ratio 30:1 — accurate from 30× the measured distance
Audible alarm for pre-set high/low temperature thresholds
Max/min/average memory — data logging for trending
Backlit display for use in dark loco pit environments', 'Non-contact measurement eliminates risk of contact with hot rotating components — critical on live locomotive bogies', 'Bearing hot-box detection on locomotive bogies
Brake drum temperature monitoring
Electrical panel hotspot scanning
Engine cooling system temperature profiling', 'Range: –50 to 1000°C
Accuracy: ±1% or ±1°C
D:S Ratio: 30:1
Response Time: 500 ms
Display: Backlit LCD
Power: 9V battery'),
('NGP-TST-016', 'Vibration Analyser & Data Logger', 'vibration-analyser', 'Testing & Inspection', 'Tri-axial vibration analyser for predictive maintenance of locomotive and track machinery bearings. FFT analysis identifies bearing defect frequencies months before failure.', 18500, 24000, 18, 4, 1, 'Professional', 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=800&q=80', TRUE, FALSE, 4.9, 6, '9031', 'Detect Bearing Failure Months in Advance.', 'Tri-axial vibration analyser for predictive maintenance of locomotive and track machinery bearings. FFT analysis identifies bearing defect frequencies months before failure.', '18 months.', 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=800&q=80', 'Tri-axial measurement — X, Y, Z simultaneously
FFT analysis — identifies specific bearing defect frequencies
ISO 10816 vibration severity zone indicators (A/B/C/D)
Bluetooth export to maintenance management software
Onboard trending — compare measurements over time
IP67 — fully weatherproof for track-side use', 'Predictive maintenance catches bearing failure weeks before it occurs — preventing unplanned traction system failures', 'Locomotive traction motor bearing condition monitoring
Wheel bearing defect detection (WILD detection supplement)
Track maintenance machinery gearbox monitoring
Pump and fan bearing predictive maintenance', 'Measurement: Tri-axial (X, Y, Z)
Frequency Range: 0.5–10,000 Hz
Standard: ISO 10816-3
Connectivity: Bluetooth 5.0
Protection: IP67
Battery: 24 hours continuous'),
('NGP-PPE-017', 'Flame-Retardant Coverall (FR-2 Grade)', 'flame-retardant-coverall', 'PPE & Workwear', 'Electrical maintenance on locomotives involves live 750V DC traction systems and high-ampere distribution panels. A single arc flash event can produce temperatures exceeding 19,000°C. Standard cotton clothing ignites immediately and continues burning — causing 3rd-degree burns over 80% of the body in under 1 second. This FR-2 coverall uses inherent flame-retardant fibres that self-extinguish within 2 seconds when the ignition source is removed — providing the critical 4-second escape window. The ATPV rating of 12 cal/cm² means it protects against arc energies up to 12 calories per square centimetre — covering all standard railway distribution panel maintenance tasks.', 3800, 5000, 12, 40, 1, NULL, 'https://images.unsplash.com/photo-1598300042247-d088f8ab3a91?w=800&q=80', TRUE, FALSE, 4.7, 14, '6211', 'Survive Arc Flash. Return Home Safe.', 'EN ISO 11612 certified single-piece flame-retardant coverall for electrical maintenance and loco repair. Provides 4-second critical escape time in arc flash and petroleum fire — the difference between life and death.', '12 months against manufacturing defects. FR properties certified for 100 industrial washes — track wash count with laundry tag. Replace if fabric shows visible char damage or tears.', 'https://images.unsplash.com/photo-1598300042247-d088f8ab3a91?w=800&q=80', 'EN ISO 11612:2015 certified (A1, B1, C1, F1 properties)
Arc Flash Category 2 — ATPV 12 cal/cm² (NFPA 70E)
Inherently FR fabric — cannot be washed out (unlike treated cotton)
Concealed brass zip prevents metal flash-through during arc exposure
Elastic waist and gusset knees for unrestricted movement in loco pits
Reflective strips on arms and legs — Class 2 visibility over FR layer', 'Inherent FR — properties never wash out unlike treated cotton workwear
Dual certification (arc flash + heat/flame) means a single garment for electrical and thermal hazards
Reflective strips qualify for Class 2 hi-vis when working near moving trains', 'Locomotive electrical and traction system maintenance
High-voltage railway distribution panel work
Petroleum and fuel system handling at loco sheds
Welding and cutting operations (with appropriate supplemental protection)', 'Standard: EN ISO 11612:2015 (A1B1C1F1)
Arc Rating: ATPV 12 cal/cm² (NFPA 70E Cat 2)
Fabric: Proban® FR Cotton Twill, 350 gsm
FR Type: Inherent (not wash-out treatment)
Closure: Concealed brass zip (anti-flash)
Sizes: S / M / L / XL / XXL / 3XL / 4XL
Colour: Navy Blue, Orange (choice at order)
Wash Durability: 100 industrial washes at 60°C'),
('NGP-PPE-018', 'Arc-Flash Face Shield (12 cal/cm²)', 'arc-flash-face-shield', 'PPE & Workwear', '12 cal/cm² ATPV arc flash face shield for electrical maintenance. NFPA 70E and IEC 61482-2 certified. Anti-fog, anti-scratch polycarbonate lens with ratchet headgear.', 2900, 3800, 18, 40, 1, NULL, 'https://images.unsplash.com/photo-1582139329536-e7284fece509?w=800&q=80', TRUE, FALSE, 4.8, 8, '6506', 'Face + Eye Protection Against Arc Flash.', '12 cal/cm² ATPV arc flash face shield for electrical maintenance. NFPA 70E and IEC 61482-2 certified. Anti-fog, anti-scratch polycarbonate lens with ratchet headgear.', '6 months.', 'https://images.unsplash.com/photo-1582139329536-e7284fece509?w=800&q=80', 'ATPV 12 cal/cm² — NFPA 70E Category 2
IEC 61482-2 certified (Class 2)
Anti-fog + anti-scratch polycarbonate lens
Ratchet headgear — adjusts without removing helmet
UV protection factor >99%
Mounts over standard safety helmet', 'Face + eye protection in one — eliminates gap between safety glasses and face shield', 'Electrical panel and switchgear maintenance
Loco traction system work
Battery room maintenance', 'Arc Rating: 12 cal/cm² (Cat 2)
Standard: NFPA 70E, IEC 61482-2 Class 2
Lens: Polycarbonate, 3.5 mm
Weight: 380 g'),
('NGP-PPE-019', 'Professional Knee Pad Set', 'professional-knee-pads', 'PPE & Workwear', 'Heavy-duty knee pads for track workers and loco maintenance technicians who work on their knees for hours. Memory foam + ABS shell combination — the only knee pad for industrial use.', 890, 1200, 18, 120, 1, 'Bestseller', 'https://images.unsplash.com/photo-1517672651691-24622a91b550?w=800&q=80', TRUE, FALSE, 4.6, 27, '6307', 'Zero Compromise. All-Day Kneeling Comfort.', 'Heavy-duty knee pads for track workers and loco maintenance technicians who work on their knees for hours. Memory foam + ABS shell combination — the only knee pad for industrial use.', '6 months. Replacement foam available.', 'https://images.unsplash.com/photo-1517672651691-24622a91b550?w=800&q=80', 'Memory foam inner — moulds to individual knee shape
ABS hard shell — protects against stone, gravel, and rail edges
Non-slip rubber base — stable on wet and oily surfaces
Adjustable double velcro straps — fits 36–54 cm circumference
Fits over work trousers without slipping down
Replaceable foam inserts', 'Memory foam shape retention reduces fatigue by 60% vs standard foam — essential for 8-hour maintenance shifts', 'Track fastener installation and maintenance
Loco pit and undercarriage maintenance
Tiling and flooring work
Any extended kneeling task', 'Shell: ABS plastic, 8 mm
Cushion: Memory foam, 20 mm
Base: Non-slip rubber
Strap: Double velcro, adjustable
Fits: 36–54 cm knee circumference'),
('NGP-PPE-020', 'Cut-Resistant Sleeve Protectors (Pair)', 'cut-resistant-sleeves', 'PPE & Workwear', 'ANSI A4 cut-resistant sleeves from wrist to elbow. UHMWPE fibre construction. Worn over gloves to protect forearms when handling sharp metal components.', 420, 560, 12, 120, 1, NULL, 'https://images.unsplash.com/photo-1567361808960-dec9cb578182?w=800&q=80', TRUE, FALSE, 4.5, 20, '6116', 'ANSI A4 Cut Resistance. Wrist to Elbow.', 'ANSI A4 cut-resistant sleeves from wrist to elbow. UHMWPE fibre construction. Worn over gloves to protect forearms when handling sharp metal components.', '3 months. Replace when visible cuts or abrasion damage are present.', 'https://images.unsplash.com/photo-1567361808960-dec9cb578182?w=800&q=80', 'ANSI A4 cut resistance — handles sheet metal and sharp rail clips
UHMWPE fibre (Ultra-High Molecular Weight Polyethylene)
Machine washable (40°C) — maintain cut resistance after 50 washes
Thumb opening for secure positioning
One size fits most — 18 cm wide × 45 cm long
Pair supplied', 'Worn over Level D gloves — combined cut protection covers all forearm and hand laceration risks', 'Sheet metal handling in fabrication shops
Rail clip and spring clip installation
Glass and ceramic component handling
Worn over cut gloves for double protection on extreme-risk tasks', 'Cut Standard: ANSI A4
Material: UHMWPE fibre
Length: 45 cm (wrist to elbow)
Width: 18 cm (flat)
Washable: Yes, 40°C')
ON CONFLICT (sku) DO NOTHING;
