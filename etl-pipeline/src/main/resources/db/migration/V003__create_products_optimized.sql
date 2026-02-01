-- DROP VIEW a TABLE
DROP VIEW IF EXISTS catalog.v_products_export_optimized;
DROP TABLE IF EXISTS catalog.products_optimized;

-- CREATE TABLE
CREATE TABLE IF NOT EXISTS catalog.products_optimized (
                                            id SERIAL PRIMARY KEY,
                                            product_id INTEGER NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,

    -- 1. IDENTITA & STRATÉGIA
                                            strategy_type VARCHAR(50),           -- 'CHEMICALS', 'PAPER', 'HARDWARE', 'GENERIC'
                                            parent_product_name VARCHAR(500),    -- Názov očistený o varianty (od LLM)
                                            grouping_key VARCHAR(200),           -- Deterministický slug (od Javy)
                                            brand_detected VARCHAR(100),
                                            is_variant BOOLEAN DEFAULT FALSE,

    -- 2. PREDICTIVE SEO
                                            suggested_focus_keyword VARCHAR(150),
                                            search_intent VARCHAR(50),           -- 'Transactional', 'Informational'

    -- 3. ATOMÁRNY OBSAH (Suroviny pre HTML šablónu)
                                            name_h1 VARCHAR(500),
                                            section_problem TEXT,                -- Pain point
                                            section_solution TEXT,               -- Technical specs
                                            section_usage TEXT,                  -- Application guide

                                            html_final TEXT,                     -- Finálne zostavené HTML (cache)
                                            image_alt_text VARCHAR(255),
                                            meta_title VARCHAR(70),
                                            meta_description VARCHAR(160),
                                            short_description TEXT,

    -- 4. PÔVODNÉ DÁTA (pre Java servisu)
                                            original_name VARCHAR(500),
                                            original_description TEXT,
                                            sku VARCHAR(50),

    -- 5. ŠTRUKTÚROVANÉ DÁTA
                                            json_specs JSONB,                    -- Extrahované parametre
                                            json_faq JSONB,                      -- FAQ
                                            json_features JSONB,                 -- Technické benefity

    -- 6. DÁTA & KVALITA
                                            data_quality_score INTEGER,          -- 0-100 (vážené skóre)
                                            missing_critical_specs JSONB,        -- Zoznam chýbajúcich povinných polí ["ph", "objem"]

    -- 7. SYSTÉM
                                            status VARCHAR(20) DEFAULT 'pending',
                                            model_used VARCHAR(50),
                                            processed_at TIMESTAMPTZ,
                                            updated_at TIMESTAMPTZ DEFAULT NOW(),

                                            CONSTRAINT products_optimized_product_uk UNIQUE(product_id)
);

-- INDEXY
CREATE INDEX idx_po_strategy ON catalog.products_optimized(strategy_type);
CREATE INDEX idx_po_quality ON catalog.products_optimized(data_quality_score);
CREATE INDEX idx_po_brand ON catalog.products_optimized(brand_detected text_ops);
CREATE INDEX idx_po_grouping_key ON catalog.products_optimized(grouping_key text_ops);
CREATE INDEX idx_po_specs ON catalog.products_optimized(json_specs jsonb_ops);
CREATE INDEX idx_po_status ON catalog.products_optimized(status text_ops);

ALTER TABLE catalog.products_optimized
    ADD COLUMN validation_warnings TEXT;

-- NAPLNENIE DÁTAMI z hlavnej tabuľky products
INSERT INTO catalog.products_optimized (product_id, sku, original_name, original_description, status)
SELECT id, sku, name, description, 'pending'
FROM catalog.products
ON CONFLICT (product_id) DO NOTHING;

