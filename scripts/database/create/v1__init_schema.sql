-- ==========================================
-- TITAN MOONID v4.0 Mini - Init Schema
-- ==========================================

-- 1. CONFIGURATION LAYER
CREATE TABLE supplier_profiles (
    supplier_code VARCHAR(50) PRIMARY KEY,
    feed_url TEXT,
    feed_type VARCHAR(20), -- 'XML_STREAM', 'SOAP_CLIENT', 'CSV_STREAM'
    is_active BOOLEAN DEFAULT TRUE,
    priority_price INT DEFAULT 10,
    priority_content INT DEFAULT 10,
    priority_stock INT DEFAULT 10
);

CREATE TABLE supplier_extraction_rules (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50) REFERENCES supplier_profiles(supplier_code),
    version INT NOT NULL,
    rules_json JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (supplier_code, version)
);

-- 2. RAW LAYER
CREATE TABLE sys_batches (
    batch_id UUID PRIMARY KEY,
    supplier_code VARCHAR(50),
    status VARCHAR(20),
    started_at TIMESTAMPTZ DEFAULT NOW(),
    stats_summary JSONB
);

-- Partitioned Table pre Raw Items
CREATE TABLE raw_items (
    id BIGSERIAL,
    supplier_code VARCHAR(50) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    item_hash CHAR(64),
    payload JSONB,
    PRIMARY KEY (supplier_code, id)
) PARTITION BY LIST (supplier_code);

-- Príprava partície pre HUMED (prvého dodávateľa)
CREATE TABLE raw_items_humed PARTITION OF raw_items FOR VALUES IN ('humed');

CREATE TABLE raw_item_presence (
    supplier_code VARCHAR(50),
    external_id VARCHAR(100),
    batch_id UUID REFERENCES sys_batches(batch_id),
    seen_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (supplier_code, external_id, batch_id)
);
CREATE INDEX idx_presence_last_seen ON raw_item_presence (supplier_code, external_id, seen_at DESC);

-- 3. STAGING LAYER
CREATE TABLE staging_unified (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50),
    external_id VARCHAR(100),
    
    extracted_brand VARCHAR(100),
    extracted_mpn VARCHAR(100),
    extracted_system VARCHAR(20),
    
    price_buy_total NUMERIC(12,4),
    pack_amount INT DEFAULT 1,
    pack_unit VARCHAR(20),
    price_per_unit NUMERIC(12,4),
    
    stock_qty INT,
    title TEXT,
    description_clean TEXT,
    attributes_raw JSONB,
    
    last_batch_id UUID,
    is_active BOOLEAN DEFAULT TRUE,
    UNIQUE (supplier_code, external_id)
);

CREATE TABLE staging_identity_guard (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50),
    external_id VARCHAR(100),
    last_valid_brand VARCHAR(100),
    last_valid_mpn VARCHAR(100),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (supplier_code, external_id)
);

-- 4. MASTER LAYER
CREATE TABLE master_products (
    id BIGSERIAL PRIMARY KEY,
    moonid_sku VARCHAR(150) UNIQUE NOT NULL, -- BRAND-MPN-SYSTEM-PACK
    brand VARCHAR(100),
    mpn VARCHAR(100),
    system_type VARCHAR(20),
    base_name TEXT,
    
    short_description TEXT,
    full_description TEXT,
    attributes JSONB,
    
    woo_id BIGINT,
    woo_snapshot_hash CHAR(64),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. MEDIA
CREATE TABLE media_registry (
    content_hash CHAR(64) PRIMARY KEY,
    s3_key TEXT NOT NULL,
    size_bytes INT,
    mime_type VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE media_url_tracker (
    url_normalized_hash CHAR(64) PRIMARY KEY,
    original_url TEXT,
    content_hash CHAR(64) REFERENCES media_registry(content_hash),
    last_checked_at TIMESTAMPTZ
);
