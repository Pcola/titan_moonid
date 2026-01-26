-- ============================================================
-- V001: Vytvorenie schém a základných tabuliek
-- ETL Pipeline pre B2B E-commerce
-- ============================================================

-- Potrebné rozšírenia
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================
-- SCHEMA: staging
-- Účel: Surové dáta z feedov, 1:1 mapovanie
-- =============================================================
CREATE SCHEMA IF NOT EXISTS staging;

-- -------------------------------------------------------------
-- HUMED: Flat tabuľka so všetkými dátami
-- -------------------------------------------------------------
CREATE TABLE staging.humed_raw (
    id SERIAL PRIMARY KEY,
    
    -- Identifikátory z feedu
    feed_id VARCHAR(20) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    gtin VARCHAR(50),
    
    -- Základné údaje
    title TEXT NOT NULL,
    description TEXT,
    link TEXT,
    
    -- Ceny
    price_purchase NUMERIC(10,4),
    price_retail NUMERIC(10,4),
    
    -- Fyzické vlastnosti
    weight_grams INTEGER,
    availability VARCHAR(20),
    condition VARCHAR(20) DEFAULT 'new',
    
    -- Štruktúrované dáta
    categories JSONB NOT NULL DEFAULT '[]',
    images JSONB NOT NULL DEFAULT '[]',
    attributes JSONB NOT NULL DEFAULT '{}',
    
    -- Metadata
    raw_xml TEXT,
    imported_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    checksum VARCHAR(64),
    is_excluded BOOLEAN DEFAULT FALSE,
    exclusion_reason VARCHAR(100),
    
    CONSTRAINT humed_raw_feed_id_uk UNIQUE(feed_id),
    CONSTRAINT humed_raw_sku_uk UNIQUE(sku)
);

CREATE INDEX idx_humed_raw_title_gin ON staging.humed_raw 
    USING GIN (to_tsvector('simple', title));
CREATE INDEX idx_humed_raw_categories_gin ON staging.humed_raw 
    USING GIN (categories);
CREATE INDEX idx_humed_raw_excluded ON staging.humed_raw(is_excluded) 
    WHERE is_excluded = FALSE;

-- -------------------------------------------------------------
-- HUMED: Sync log
-- -------------------------------------------------------------
CREATE TABLE staging.humed_sync_log (
    id SERIAL PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    products_total INTEGER DEFAULT 0,
    products_new INTEGER DEFAULT 0,
    products_updated INTEGER DEFAULT 0,
    products_unchanged INTEGER DEFAULT 0,
    products_excluded INTEGER DEFAULT 0,
    error_message TEXT,
    feed_checksum VARCHAR(64)
);

-- -------------------------------------------------------------
-- CORWELL: Hlavná tabuľka produktov
-- -------------------------------------------------------------
CREATE TABLE staging.corwell_raw (
    id SERIAL PRIMARY KEY,
    
    cikkid INTEGER NOT NULL,
    cikkszam VARCHAR(50) NOT NULL,
    cikkszam2 VARCHAR(50),
    
    cikknev TEXT,
    leiras TEXT,
    cikknev_sk TEXT,
    leiras_sk TEXT,
    
    cikkcsoportkod VARCHAR(50),
    cikkcsoportnev TEXT,
    focsoportkod VARCHAR(50),
    focsoportnev TEXT,
    
    tipus INTEGER,
    beszerzesiallapot INTEGER,
    webmegjel INTEGER,
    
    me VARCHAR(20),
    alapme VARCHAR(20),
    alapmenny NUMERIC(10,4),
    tomeg NUMERIC(10,4),
    
    xmeret NUMERIC(10,4),
    ymeret NUMERIC(10,4),
    zmeret NUMERIC(10,4),
    
    gyarto VARCHAR(100),
    afakulcs NUMERIC(5,2),
    kshszam VARCHAR(20),
    
    images JSONB NOT NULL DEFAULT '[]',
    
    webigendatum DATE,
    imported_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    checksum VARCHAR(64),
    is_excluded BOOLEAN DEFAULT FALSE,
    exclusion_reason VARCHAR(100),
    
    CONSTRAINT corwell_raw_cikkid_uk UNIQUE(cikkid),
    CONSTRAINT corwell_raw_cikkszam_uk UNIQUE(cikkszam)
);

-- -------------------------------------------------------------
-- CORWELL: Cenník
-- -------------------------------------------------------------
CREATE TABLE staging.corwell_prices (
    id SERIAL PRIMARY KEY,
    cikkid INTEGER NOT NULL,
    
    listaar NUMERIC(12,4),
    ar NUMERIC(12,4),
    akcios_ar NUMERIC(12,4),
    devizanem VARCHAR(10),
    
    valid_from TIMESTAMPTZ DEFAULT NOW(),
    valid_to TIMESTAMPTZ,
    
    CONSTRAINT corwell_prices_current_uk UNIQUE(cikkid, valid_to)
);

CREATE INDEX idx_corwell_prices_cikkid ON staging.corwell_prices(cikkid);

-- -------------------------------------------------------------
-- CORWELL: Zásoby
-- -------------------------------------------------------------
CREATE TABLE staging.corwell_stock (
    id SERIAL PRIMARY KEY,
    cikkid INTEGER NOT NULL,
    cikkszam VARCHAR(50) NOT NULL,
    
    stock_type INTEGER NOT NULL,
    quantity NUMERIC(12,4),
    stock_text VARCHAR(100),
    
    checked_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_corwell_stock_lookup ON staging.corwell_stock(cikkid, checked_at DESC);

-- -------------------------------------------------------------
-- CORWELL: Sync log
-- -------------------------------------------------------------
CREATE TABLE staging.corwell_sync_log (
    id SERIAL PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    records_processed INTEGER DEFAULT 0,
    error_message TEXT
);


-- =============================================================
-- SCHEMA: catalog
-- Účel: Normalizované produkty, vlastný category tree
-- =============================================================
CREATE SCHEMA IF NOT EXISTS catalog;

-- -------------------------------------------------------------
-- CATEGORIES: Vlastný strom kategórií
-- -------------------------------------------------------------
CREATE TABLE catalog.categories (
    id SERIAL PRIMARY KEY,
    
    parent_id INTEGER REFERENCES catalog.categories(id),
    level INTEGER NOT NULL DEFAULT 1,
    path LTREE,
    
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    
    menu_name VARCHAR(100),
    h1_seo_name VARCHAR(200),
    
    description TEXT,
    meta_title VARCHAR(200),
    meta_description VARCHAR(500),
    
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT categories_slug_uk UNIQUE(slug)
);

CREATE INDEX idx_categories_path ON catalog.categories USING GIST(path);
CREATE INDEX idx_categories_parent ON catalog.categories(parent_id);
CREATE INDEX idx_categories_active ON catalog.categories(is_active) WHERE is_active = TRUE;

-- -------------------------------------------------------------
-- CATEGORY_RULES: Pravidlá pre automatické mapovanie
-- -------------------------------------------------------------
CREATE TABLE catalog.category_rules (
    id SERIAL PRIMARY KEY,
    
    source VARCHAR(20) NOT NULL,
    
    source_category_id VARCHAR(50),
    source_category_exact TEXT,
    source_category_pattern TEXT,
    title_pattern TEXT,
    
    target_category_id INTEGER NOT NULL REFERENCES catalog.categories(id),
    
    priority INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(100) DEFAULT 'system',
    notes TEXT,
    
    CONSTRAINT category_rules_has_pattern_ck CHECK (
        source_category_id IS NOT NULL OR 
        source_category_exact IS NOT NULL OR
        source_category_pattern IS NOT NULL OR 
        title_pattern IS NOT NULL
    )
);

CREATE INDEX idx_category_rules_lookup ON catalog.category_rules(source, is_active, priority)
    WHERE is_active = TRUE;

-- -------------------------------------------------------------
-- CATEGORY_EXCLUSIONS: Zdrojové kategórie na vylúčenie
-- -------------------------------------------------------------
CREATE TABLE catalog.category_exclusions (
    id SERIAL PRIMARY KEY,
    
    source VARCHAR(20) NOT NULL,
    source_category_pattern TEXT NOT NULL,
    
    reason VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------------------------------------------
-- CATEGORY_MAPPING_LOG: Audit mapovania
-- -------------------------------------------------------------
CREATE TABLE catalog.category_mapping_log (
    id SERIAL PRIMARY KEY,
    
    source VARCHAR(20) NOT NULL,
    source_product_id VARCHAR(50) NOT NULL,
    source_sku VARCHAR(50),
    source_category_raw TEXT,
    
    matched_rule_id INTEGER REFERENCES catalog.category_rules(id),
    target_category_id INTEGER REFERENCES catalog.categories(id),
    match_type VARCHAR(20) NOT NULL,
    
    mapped_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mapping_log_unmapped ON catalog.category_mapping_log(match_type)
    WHERE match_type = 'unmapped';

-- -------------------------------------------------------------
-- PRODUCTS: Normalizované produkty
-- -------------------------------------------------------------
CREATE TABLE catalog.products (
    id SERIAL PRIMARY KEY,
    
    sku VARCHAR(50) NOT NULL,
    
    name VARCHAR(500) NOT NULL,
    description TEXT,
    short_description VARCHAR(1000),
    
    category_id INTEGER REFERENCES catalog.categories(id),
    
    price_cost NUMERIC(12,4),
    price_b2b NUMERIC(12,4),
    margin_percent NUMERIC(5,2),
    
    weight_kg NUMERIC(10,4),
    
    pack_quantity INTEGER,
    pallet_quantity INTEGER,
    min_order_quantity INTEGER DEFAULT 1,
    
    images JSONB NOT NULL DEFAULT '[]',
    attributes JSONB NOT NULL DEFAULT '{}',
    
    is_active BOOLEAN DEFAULT TRUE,
    stock_status VARCHAR(20) DEFAULT 'instock',
    
    meta_title VARCHAR(200),
    meta_description VARCHAR(500),
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT products_sku_uk UNIQUE(sku)
);

CREATE INDEX idx_products_category ON catalog.products(category_id);
CREATE INDEX idx_products_active ON catalog.products(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_products_name_gin ON catalog.products USING GIN (to_tsvector('simple', name));

-- -------------------------------------------------------------
-- PRODUCT_SOURCES: Prepojenie na zdrojové feedy
-- -------------------------------------------------------------
CREATE TABLE catalog.product_sources (
    id SERIAL PRIMARY KEY,
    
    product_id INTEGER NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    
    source VARCHAR(20) NOT NULL,
    source_id VARCHAR(50) NOT NULL,
    source_sku VARCHAR(50),
    
    source_price_purchase NUMERIC(12,4),
    source_price_retail NUMERIC(12,4),
    
    is_primary BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 1,
    
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE,
    
    CONSTRAINT product_sources_source_uk UNIQUE(source, source_id)
);

CREATE INDEX idx_product_sources_product ON catalog.product_sources(product_id);

-- -------------------------------------------------------------
-- PRICE_RULES: Pravidlá pre výpočet cien
-- -------------------------------------------------------------
CREATE TABLE catalog.price_rules (
    id SERIAL PRIMARY KEY,
    
    category_id INTEGER REFERENCES catalog.categories(id),
    source VARCHAR(20),
    
    rule_type VARCHAR(20) NOT NULL,
    value NUMERIC(10,4) NOT NULL,
    
    priority INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);


-- =============================================================
-- SCHEMA: export
-- Účel: WooCommerce-ready formát
-- =============================================================
CREATE SCHEMA IF NOT EXISTS export;

-- -------------------------------------------------------------
-- WOOCOMMERCE_CATEGORIES
-- -------------------------------------------------------------
CREATE TABLE export.woocommerce_categories (
    id SERIAL PRIMARY KEY,
    
    category_id INTEGER NOT NULL REFERENCES catalog.categories(id),
    woo_id INTEGER,
    woo_parent_id INTEGER,
    
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    
    sync_status VARCHAR(20) DEFAULT 'pending',
    last_synced_at TIMESTAMPTZ,
    
    CONSTRAINT woo_categories_cat_uk UNIQUE(category_id)
);

-- -------------------------------------------------------------
-- WOOCOMMERCE_PRODUCTS
-- -------------------------------------------------------------
CREATE TABLE export.woocommerce_products (
    id SERIAL PRIMARY KEY,
    
    product_id INTEGER NOT NULL REFERENCES catalog.products(id),
    
    woo_id INTEGER,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    short_description VARCHAR(1000),
    
    regular_price NUMERIC(12,4),
    
    category_ids JSONB NOT NULL DEFAULT '[]',
    category_names JSONB NOT NULL DEFAULT '[]',
    
    images JSONB NOT NULL DEFAULT '[]',
    attributes JSONB NOT NULL DEFAULT '[]',
    
    status VARCHAR(20) DEFAULT 'publish',
    stock_status VARCHAR(20) DEFAULT 'instock',
    manage_stock BOOLEAN DEFAULT FALSE,
    
    sync_status VARCHAR(20) DEFAULT 'pending',
    last_synced_at TIMESTAMPTZ,
    sync_error TEXT,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT woo_products_product_uk UNIQUE(product_id),
    CONSTRAINT woo_products_sku_uk UNIQUE(sku)
);

-- -------------------------------------------------------------
-- WOOCOMMERCE_SYNC_LOG
-- -------------------------------------------------------------
CREATE TABLE export.woocommerce_sync_log (
    id SERIAL PRIMARY KEY,
    
    sync_type VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    
    products_created INTEGER DEFAULT 0,
    products_updated INTEGER DEFAULT 0,
    products_failed INTEGER DEFAULT 0,
    categories_synced INTEGER DEFAULT 0,
    
    error_log JSONB DEFAULT '[]'
);


-- =============================================================
-- POMOCNÉ FUNKCIE
-- =============================================================

-- Funkcia na automatickú aktualizáciu LTREE path
CREATE OR REPLACE FUNCTION catalog.update_category_path()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.parent_id IS NULL THEN
        NEW.path = text2ltree(NEW.slug);
        NEW.level = 1;
    ELSE
        SELECT path || text2ltree(NEW.slug), level + 1
        INTO NEW.path, NEW.level
        FROM catalog.categories
        WHERE id = NEW.parent_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_categories_path
    BEFORE INSERT OR UPDATE OF parent_id, slug ON catalog.categories
    FOR EACH ROW
    EXECUTE FUNCTION catalog.update_category_path();

-- Funkcia na automatickú aktualizáciu updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_humed_raw_updated
    BEFORE UPDATE ON staging.humed_raw
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_corwell_raw_updated
    BEFORE UPDATE ON staging.corwell_raw
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_products_updated
    BEFORE UPDATE ON catalog.products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_categories_updated
    BEFORE UPDATE ON catalog.categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_woo_products_updated
    BEFORE UPDATE ON export.woocommerce_products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
