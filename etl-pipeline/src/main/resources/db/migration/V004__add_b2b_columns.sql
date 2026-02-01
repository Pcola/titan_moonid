-- V004: Pridanie B2B a AEO/GEO/LLM stĺpcov

-- 1. B2B LOGISTIKA (pre automatizovaný nákup)
ALTER TABLE catalog.products_optimized
    ADD COLUMN ean_gtin VARCHAR(20),              -- EAN/GTIN kód
    ADD COLUMN  packaging_unit VARCHAR(50),        -- 'kartón', 'paleta', 'balík'
    ADD COLUMN  packaging_quantity INTEGER,        -- Počet ks v balení (kartón)
    ADD COLUMN  pallet_quantity INTEGER,           -- Počet ks/balení na palete
    ADD COLUMN  moq INTEGER DEFAULT 1;
-- Minimum Order Quantity

-- 2. AEO/GEO/LLM SIGNÁLY (pre AI agentov a lokálne vyhľadávanie)
ALTER TABLE catalog.products_optimized
    ADD COLUMN target_segments JSONB,             -- ["hotely", "kancelárie", "gastro", "nemocnice"]
    ADD COLUMN  locality_country VARCHAR(5) DEFAULT 'SK',  -- ISO kód krajiny
    ADD COLUMN  certifications JSONB;
-- ["HACCP", "ISO 9001", "EN 14476"]

-- 3. INDEXY pre nové stĺpce
CREATE INDEX idx_po_ean ON catalog.products_optimized (ean_gtin) WHERE ean_gtin IS NOT NULL;
CREATE INDEX idx_po_segments ON catalog.products_optimized USING GIN(target_segments jsonb_path_ops);
CREATE INDEX idx_po_certifications ON catalog.products_optimized USING GIN(certifications jsonb_path_ops);

-- 4. KOMENTÁRE
COMMENT
ON COLUMN catalog.products_optimized.ean_gtin IS 'EAN/GTIN čiarový kód pre B2B automatizovaný nákup';
COMMENT
ON COLUMN catalog.products_optimized.packaging_unit IS 'Typ balenia: kartón, paleta, balík';
COMMENT
ON COLUMN catalog.products_optimized.packaging_quantity IS 'Počet kusov v balení (napr. 12 ks v kartóne)';
COMMENT
ON COLUMN catalog.products_optimized.pallet_quantity IS 'Počet kusov/balení na palete';
COMMENT
ON COLUMN catalog.products_optimized.moq IS 'Minimum Order Quantity - minimálne objednávacie množstvo';
COMMENT
ON COLUMN catalog.products_optimized.target_segments IS 'Cieľové segmenty: hotely, kancelárie, gastro, nemocnice, školy';
COMMENT
ON COLUMN catalog.products_optimized.locality_country IS 'ISO kód krajiny pre GEO signály';
COMMENT
ON COLUMN catalog.products_optimized.certifications IS 'Certifikácie: HACCP, ISO, EN normy, biocídne povolenia';
