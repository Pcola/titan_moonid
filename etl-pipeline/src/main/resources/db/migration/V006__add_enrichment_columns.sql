-- =============================================================
-- V006: Pridanie stĺpcov pre manufacturer enrichment
-- =============================================================
-- Tieto stĺpce ukladajú dáta získané z oficiálnych stránok výrobcov
-- pomocou Gemini API s Google Search grounding

-- Manufacturer SKU (môže sa líšiť od dodávateľského SKU)
ALTER TABLE catalog.products_optimized
    ADD COLUMN IF NOT EXISTS manufacturer_sku VARCHAR(100);

-- URL produktovej stránky výrobcu
ALTER TABLE catalog.products_optimized
    ADD COLUMN IF NOT EXISTS manufacturer_url TEXT;

-- Zdroj enrichmentu: 'humed_feed', 'manufacturer_website', 'gemini_inferred'
ALTER TABLE catalog.products_optimized
    ADD COLUMN IF NOT EXISTS enrichment_source VARCHAR(50) DEFAULT 'humed_feed';

-- Spoľahlivosť enrichmentu (0.00 - 1.00)
-- 0.9+ = priamo z produktovej stránky
-- 0.5-0.8 = z katalógu alebo inferované
ALTER TABLE catalog.products_optimized
    ADD COLUMN IF NOT EXISTS enrichment_confidence DECIMAL(3,2);

-- Zdroje použité pri grounding (array URL)
ALTER TABLE catalog.products_optimized
    ADD COLUMN IF NOT EXISTS grounding_sources JSONB;

-- =============================================================
-- Indexy pre efektívne vyhľadávanie
-- =============================================================

-- Index pre produkty bez EAN (pre prioritizáciu enrichmentu)
CREATE INDEX IF NOT EXISTS idx_products_missing_ean
    ON catalog.products_optimized(ean_gtin)
    WHERE ean_gtin IS NULL;

-- Index pre produkty s manufacturer enrichmentom
CREATE INDEX IF NOT EXISTS idx_products_manufacturer_enriched
    ON catalog.products_optimized(enrichment_source)
    WHERE enrichment_source = 'manufacturer_website';

-- Index pre confidence score (pre identifikáciu produktov s nízkym confidence)
CREATE INDEX IF NOT EXISTS idx_products_enrichment_confidence
    ON catalog.products_optimized(enrichment_confidence)
    WHERE enrichment_confidence IS NOT NULL;

-- =============================================================
-- Komentáre pre dokumentáciu
-- =============================================================
COMMENT ON COLUMN catalog.products_optimized.manufacturer_sku IS
    'SKU produktu priamo od výrobcu (môže sa líšiť od dodávateľského SKU)';

COMMENT ON COLUMN catalog.products_optimized.manufacturer_url IS
    'URL produktovej stránky na oficiálnom webe výrobcu';

COMMENT ON COLUMN catalog.products_optimized.enrichment_source IS
    'Zdroj produktových dát: humed_feed, manufacturer_website, gemini_inferred';

COMMENT ON COLUMN catalog.products_optimized.enrichment_confidence IS
    'Spoľahlivosť enrichmentu (0-1). 0.9+ = overené z produktovej stránky';

COMMENT ON COLUMN catalog.products_optimized.grounding_sources IS
    'JSON array URL zdrojov použitých pri Gemini grounding';
