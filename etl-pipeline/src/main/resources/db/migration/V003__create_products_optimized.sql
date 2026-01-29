-- =====================================================
-- V003: Tabuľka pre LLM-optimalizované produktové dáta
-- Autor: ETL Pipeline
-- Dátum: 2026-01-27
-- =====================================================

-- Tabuľka pre optimalizované produkty
CREATE TABLE IF NOT EXISTS catalog.products_optimized (
    id SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,

    -- Pôvodné hodnoty (pre porovnanie a audit)
    original_name VARCHAR(500) NOT NULL,
    original_description TEXT,

    -- Optimalizované hodnoty od LLM
    name_optimized VARCHAR(500),
    description_optimized TEXT,
    short_description VARCHAR(500),

    -- SEO metadata
    meta_title VARCHAR(70),           -- Google zobrazuje max ~60 znakov
    meta_description VARCHAR(160),    -- Google zobrazuje max ~155 znakov
    url_slug VARCHAR(200),            -- SEO-friendly URL

    -- Varianty - grouping podobných produktov
    variant_group_id INTEGER,         -- Produkty s rovnakým ID sú varianty jedného produktu
    variant_attribute VARCHAR(50),    -- Typ varianty: "farba", "veľkosť", "vôňa", "objem"
    variant_value VARCHAR(100),       -- Hodnota varianty: "Modrá", "L", "Mango", "500ml"
    is_parent BOOLEAN DEFAULT FALSE,  -- TRUE = hlavný produkt, FALSE = varianta

    -- Detekované značky a atribúty
    brand_detected VARCHAR(100),      -- Extrahovaná značka (GO!, SATUR, KAREN, etc.)

    -- Processing metadata
    status VARCHAR(20) DEFAULT 'pending'
        CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'skipped')),
    error_message TEXT,
    model_used VARCHAR(50),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    processing_time_ms INTEGER,
    batch_job_id VARCHAR(100),        -- ID Gemini batch job

    -- Timestamps
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT products_optimized_product_uk UNIQUE(product_id)
);

-- Indexy pre rýchle vyhľadávanie
CREATE INDEX IF NOT EXISTS idx_products_optimized_status
    ON catalog.products_optimized(status);

CREATE INDEX IF NOT EXISTS idx_products_optimized_variant_group
    ON catalog.products_optimized(variant_group_id)
    WHERE variant_group_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_products_optimized_batch_job
    ON catalog.products_optimized(batch_job_id)
    WHERE batch_job_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_products_optimized_brand
    ON catalog.products_optimized(brand_detected)
    WHERE brand_detected IS NOT NULL;

-- Trigger pre updated_at
CREATE OR REPLACE FUNCTION catalog.update_products_optimized_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_products_optimized_updated ON catalog.products_optimized;
CREATE TRIGGER trg_products_optimized_updated
    BEFORE UPDATE ON catalog.products_optimized
    FOR EACH ROW
    EXECUTE FUNCTION catalog.update_products_optimized_timestamp();

-- Naplnenie tabuľky existujúcimi produktmi (len aktívne)
INSERT INTO catalog.products_optimized (product_id, original_name, original_description)
SELECT id, name, description
FROM catalog.products
WHERE is_active = true
ON CONFLICT (product_id) DO NOTHING;

-- View pre export do WooCommerce po optimalizácii
CREATE OR REPLACE VIEW catalog.v_products_export_optimized AS
WITH RECURSIVE category_path AS (
    SELECT id, parent_id, name, name::text AS full_path, level
    FROM catalog.categories
    WHERE parent_id IS NULL

    UNION ALL

    SELECT c.id, c.parent_id, c.name, cp.full_path || ' > ' || c.name::text, c.level
    FROM catalog.categories c
    JOIN category_path cp ON c.parent_id = cp.id
)
SELECT
    p.sku AS "SKU",
    COALESCE(po.name_optimized, p.name) AS "Name",
    COALESCE(po.description_optimized, p.description) AS "Description",
    COALESCE(po.short_description, LEFT(COALESCE(po.description_optimized, p.description), 200)) AS "Short description",
    p.price_b2b AS "Regular price",
    cp.full_path AS "Categories",
    REPLACE(p.images->>0, '"', '') AS "Images",
    p.stock_status AS "Stock status",
    p.weight_kg AS "Weight (kg)",
    'Balenie' AS "Attribute 1 name",
    p.attributes->>'Balenie' AS "Attribute 1 value(s)",
    COALESCE(po.meta_title, LEFT(COALESCE(po.name_optimized, p.name), 60)) AS "Meta: _yoast_wpseo_title",
    COALESCE(po.meta_description, LEFT(COALESCE(po.description_optimized, p.description), 155)) AS "Meta: _yoast_wpseo_metadesc",
    po.variant_group_id,
    po.variant_attribute,
    po.variant_value,
    po.is_parent,
    po.brand_detected,
    po.status AS optimization_status
FROM catalog.products p
LEFT JOIN catalog.products_optimized po ON po.product_id = p.id
LEFT JOIN category_path cp ON cp.id = p.category_id
WHERE p.is_active = true
ORDER BY p.sku;

-- Štatistiky pre monitoring
CREATE OR REPLACE VIEW catalog.v_optimization_stats AS
SELECT
    status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage,
    AVG(total_tokens) as avg_tokens,
    AVG(processing_time_ms) as avg_processing_ms
FROM catalog.products_optimized
GROUP BY status
ORDER BY
    CASE status
        WHEN 'completed' THEN 1
        WHEN 'processing' THEN 2
        WHEN 'pending' THEN 3
        WHEN 'failed' THEN 4
        WHEN 'skipped' THEN 5
    END;

COMMENT ON TABLE catalog.products_optimized IS 'LLM-optimalizované produktové dáta pre SEO a varianty';
COMMENT ON COLUMN catalog.products_optimized.variant_group_id IS 'Produkty s rovnakým ID tvoria skupinu variantov';
COMMENT ON COLUMN catalog.products_optimized.is_parent IS 'TRUE = hlavný produkt zobrazený v katalógu, FALSE = varianta';