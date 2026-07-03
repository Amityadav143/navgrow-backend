-- ═══════════════════════════════════════════════════════════════════════════
-- © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
-- V10 — multiple gallery images for news articles
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS image_urls TEXT;
