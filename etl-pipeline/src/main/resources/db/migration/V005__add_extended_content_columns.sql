-- V005: Pridanie rozšírených obsahových stĺpcov pre 300+ slov
-- Na základe SEO analýzy - potreba viac obsahu pre organické rankingy

-- 1. ROZŠÍRENÉ TEXTOVÉ SEKCIE
ALTER TABLE catalog.products_optimized
    ADD COLUMN section_applications TEXT,     -- Odvetvia a oblasti použitia
    ADD COLUMN  section_advantages TEXT;
-- Kľúčové výhody pre B2B

-- 2. KOMENTÁRE
COMMENT
ON COLUMN catalog.products_optimized.section_applications IS 'Odvetvia kde sa produkt používa (hotely, gastro, nemocnice...)';
COMMENT
ON COLUMN catalog.products_optimized.section_advantages IS 'Kľúčové výhody pre B2B zákazníka (úspora nákladov, efektivita...)';