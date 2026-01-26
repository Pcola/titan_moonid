-- ============================================================
-- V002: Vloženie kategórií a mapovacích pravidiel
-- Tvoj vlastný category tree + HUMED mapping rules
-- ============================================================

-- DÔLEŽITÉ: Pravidlá používajú ČISTÚ slovenčinu (po encoding fixe)
-- HUMED feed má mojibake (UTF-8 ako ISO-8859-1), napr.:
--   "HygienickÃ½ papier" -> "Hygienický papier"
--   "&gt;" -> ">"
-- 
-- Java parser MUSÍ fixnúť encoding PRED matchovaním pravidiel!
-- Pozri HumedXmlParser.fixEncoding() v Java kóde.

-- =============================================================
-- KATEGÓRIE: Tvoj vlastný strom
-- =============================================================

-- Root kategória
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(1, NULL, 'Hygiena', 'hygiena', 'Hygiena', 'Hygiena');

-- Level 1: Hlavné podkategórie
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(10, 1, 'Hygienický papier', 'hygienicky-papier', 'Hygienický papier', 'Hygienický papier'),
(20, 1, 'Mydlá', 'mydla', 'Mydlá', 'Mydlá'),
(30, 1, 'Osviežovače vzduchu', 'osviezovace-vzduchu', 'Osviežovače vzduchu', 'Osviežovače vzduchu'),
(40, 1, 'Zásobníky, dávkovače a koše', 'zasobniky-davkovace-kose-susice', 'Zásobníky, dávkovače a koše', 'Zásobníky, dávkovače, koše, sušiče a kupeľňové sety'),
(50, 1, 'Čistiace prostriedky', 'cistiace-prostriedky', 'Čistiace prostriedky', 'Čistiace prostriedky'),
(60, 1, 'Rukavice', 'rukavice-jednorazove-a-upratovacie', 'Rukavice', 'Rukavice jednorázové a upratovacie'),
(70, 1, 'Upratovacie pomôcky', 'upratovacie-pomocky', 'Upratovacie pomôcky', 'Upratovacie pomôcky'),
(80, 1, 'Netkaná textília', 'netkana-textilia-viacucelove-utierky', 'Netkaná textília', 'Netkaná textília - viacúčelové utierky'),
(90, 1, 'Vrecia', 'vrecia', 'Vrecia', 'Vrecia');

-- Level 2: Hygienický papier
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(101, 10, 'Toaletný papier', 'toaletny-papier', 'Toaletný papier', 'Toaletný papier'),
(102, 10, 'Papierové utierky', 'utierky-papierove', 'Papierové utierky', 'Utierky papierové'),
(103, 10, 'Papierové servítky', 'papierove-servitky', 'Papierové servítky', 'Papierové servítky'),
(104, 10, 'Vreckovky a kozmetické utierky', 'papierove-vreckovky-kozmeticke-utierky', 'Vreckovky a kozmetické utierky', 'Papierové vreckovky, kozmetické utierky'),
(105, 10, 'Zdravotnícke podložky', 'zdravotnicke-papierove-podlozky', 'Zdravotnícke podložky', 'Zdravotnícke papierové podložky');

-- Level 3: Papierové utierky (pod-podkategórie)
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(1021, 102, 'Rolované s perforáciou', 'rolovane-s-perforaciou', 'Rolované s perforáciou', 'Utierky papierové > Rolované s perforáciou'),
(1022, 102, 'Rolované Autocut/Matic', 'rolovane-autocut-matic', 'Rolované Autocut/Matic', 'Utierky papierové > Rolované Autocut/Matic'),
(1023, 102, 'Skladané ZZ utierky', 'skladane-zz-utierky', 'Skladané ZZ utierky', 'Utierky papierové > Skladané ZZ utierky');

-- Level 2: Mydlá
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(201, 20, 'Tekuté', 'tekute', 'Tekuté', 'Tekuté'),
(202, 20, 'Tuhé', 'tuhe', 'Tuhé', 'Tuhé'),
(203, 20, 'Speňovacie', 'spenovacie', 'Speňovacie', 'Speňovacie');

-- Level 2: Zásobníky, dávkovače a koše
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(401, 40, 'Zásobníky papierových utierok', 'zasobniky-papierovych-utierok', 'Zásobníky papierových utierok', 'Zásobníky papierových utierok'),
(402, 40, 'Zásobníky toaletného papiera', 'zasobniky-toaletneho-papiera', 'Zásobníky toaletného papiera', 'Zásobníky toaletného papiera'),
(403, 40, 'Dávkovače tekutého mydla', 'davkovace-tekuteho-mydla', 'Dávkovače tekutého mydla', 'Dávkovače tekutého mydla'),
(404, 40, 'Dávkovače mydlovej peny', 'davkovace-mydlovej-peny', 'Dávkovače mydlovej peny', 'Dávkovače mydlovej peny'),
(405, 40, 'Dávkovače dezinfekcie', 'davkovace-dezinfekcie', 'Dávkovače dezinfekcie', 'Dávkovače dezinfekcie'),
(406, 40, 'Koše', 'kose', 'Koše', 'Koše'),
(407, 40, 'Kúpeľňové sety', 'kupelnove-sety', 'Kúpeľňové sety', 'Kúpeľňové sety');

-- Level 2: Čistiace prostriedky
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(501, 50, 'Kúpeľňa, WC a odpady', 'kupelna-wc-cistice-odpadov', 'Kúpeľňa, WC a odpady', 'Kúpeľňa, WC, čističe odpadov'),
(502, 50, 'Kuchyňa a riad', 'umyvanie-riadu-a-kuchyna', 'Kuchyňa a riad', 'Umývanie riadu a kuchyňa'),
(503, 50, 'Podlahy a univerzálne čističe', 'podlaha-a-univerzal', 'Podlahy a univerzálne čističe', 'Podlaha a univerzál'),
(504, 50, 'Nábytok a okná', 'nabytok-a-okna', 'Nábytok a okná', 'Nábytok a okná'),
(505, 50, 'Profi čistiace prostriedky', 'profi-cistiace-prostriedky', 'Profi čistiace prostriedky', 'Profi čistiace prostriedky'),
(506, 50, 'WC bloky', 'wc-bloky', 'WC bloky', 'WC bloky'),
(507, 50, 'Vonné sitka do pisoárov', 'vonne-sitka-do-pisoarov', 'Vonné sitka do pisoárov', 'Vonné sitka do pisoárov'),
(508, 50, 'Pasty na ruky', 'cistiace-pasty-na-ruky', 'Pasty na ruky', 'Čistiace pasty na ruky'),
(509, 50, 'Pohlcovač vlhkosti', 'pohlcovac-vlhkosti-vzduchu', 'Pohlcovač vlhkosti', 'Pohlcovač vlhkosti vzduchu');

-- Level 2: Rukavice
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(601, 60, 'Nitrilové', 'nitrilove-rukavice', 'Nitrilové', 'Nitrilové rukavice'),
(602, 60, 'Nitrilové GOGRIP', 'nitrilove-rukavice-gogrip', 'Nitrilové GOGRIP', 'Nitrilové rukavice GOGRIP'),
(603, 60, 'Latexové', 'latexove-rukavice', 'Latexové', 'Latexové rukavice'),
(604, 60, 'Vinylové', 'vinylove-rukavice', 'Vinylové', 'Vinylové rukavice'),
(605, 60, 'Upratovacie', 'upratovacie-rukavice', 'Upratovacie', 'Upratovacie rukavice'),
(606, 60, 'Držiaky na rukavice', 'drziak-na-rukavice', 'Držiaky na rukavice', 'Držiak na rukavice');

-- Level 2: Upratovacie pomôcky
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(701, 70, 'Mopy a držiaky', 'mopy-drziaky-mopov-a-pady', 'Mopy a držiaky', 'Mopy, držiaky mopov a pady'),
(702, 70, 'Vedrá a vozíky', 'vedra-mop-sety-a-upratovacie-voziky', 'Vedrá a vozíky', 'Vedrá, mop sety a upratovacie vozíky'),
(703, 70, 'Handry a utierky', 'handry-tkane-netkane-a-mikro', 'Handry a utierky', 'Handry tkané, netkané a mikro'),
(704, 70, 'Hubky a drôtenky', 'hubky-drotenky-spongiove-utierky-kefky', 'Hubky a drôtenky', 'Hubky, drôtenky, špongiové, superstrong a uniabsorb utierky, kefky'),
(705, 70, 'Stierky a škrabky', 'stierky-skrabky-rozmyvace-a-kefy', 'Stierky a škrabky', 'Stierky, škrabky, rozmývače a kefy'),
(706, 70, 'Metly a lopatky', 'metly-metlicky-lopatky-a-ometace', 'Metly a lopatky', 'Metly, metličky, lopatky a ometače'),
(707, 70, 'Tyče a násady', 'nasady-tyce-a-teleskopicke-tyce', 'Tyče a násady', 'Násady, tyče a teleskopické tyče'),
(708, 70, 'WC kefy a zvony', 'wc-kefy-wc-sety-zvony', 'WC kefy a zvony', 'WC kefy, WC sety, zvony'),
(709, 70, 'Vlhčené upratovacie utierky', 'vlhcene-upratovacie-utierky', 'Vlhčené upratovacie utierky', 'Vlhčené upratovacie utierky'),
(710, 70, 'Mikro a kuchynské utierky', 'mikro-utierky-kuchynske-utierky-snury-stipce', 'Mikro a kuchynské utierky', 'Mikro utierky, kuchynské utierky, šnúry, štipce');

-- Level 2: Vrecia
INSERT INTO catalog.categories (id, parent_id, name, slug, menu_name, h1_seo_name) VALUES
(901, 90, 'Vrecia 10 L - 35 L', 'vrecia-10l-35l', 'Vrecia 10 L - 35 L', 'Vrecia 10 L - 35 L'),
(902, 90, 'Vrecia 60 L - 90 L', 'vrecia-60l-90l', 'Vrecia 60 L - 90 L', 'Vrecia 60 L - 90 L'),
(903, 90, 'Vrecia 100 L - 120 L', 'vrecia-100l-120l', 'Vrecia 100 L - 120 L', 'Vrecia 100 L - 120 L'),
(904, 90, 'Vrecia 150 L - 660 L', 'vrecia-150l-660l', 'Vrecia 150 L - 660 L', 'Vrecia 150 L - 660 L');

-- Reset sequence
SELECT setval('catalog.categories_id_seq', (SELECT MAX(id) FROM catalog.categories));


-- =============================================================
-- CATEGORY EXCLUSIONS: Kategórie na vylúčenie z importu
-- =============================================================

INSERT INTO catalog.category_exclusions (source, source_category_pattern, reason) VALUES
('humed', 'Kozmetika%', 'Mimo rozsahu B2B eshopu'),
('humed', 'Pracie prostriedky%', 'Mimo rozsahu B2B eshopu'),
('humed', 'Dezinfekcia%', 'Mimo rozsahu B2B eshopu'),
('humed', 'Tašky, sáčky%', 'Mimo rozsahu B2B eshopu'),
('humed', 'Baliaci materiál%', 'Mimo rozsahu B2B eshopu'),
('humed', 'Ostatné%', 'Neurčená kategória'),
('humed', 'Mikrotén%', 'Spadá pod Tašky, sáčky');


-- =============================================================
-- CATEGORY RULES: Mapovacie pravidlá HUMED -> Tvoj eshop
-- Priorita: nižšie číslo = vyššia priorita
-- =============================================================

-- -------------------------------------------------------------
-- 1. HYGIENICKÝ PAPIER
-- -------------------------------------------------------------

-- Level 3: Najšpecifickejšie pravidlá (priorita 10)
INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Hygienický papier > Utierky papierové  > Rolované s perforáciou', 1021, 10, 'Utierky - rolované s perforáciou'),
('humed', 'Hygienický papier > Utierky papierové  > Rolované Autocut/Matic', 1022, 10, 'Utierky - Autocut/Matic'),
('humed', 'Hygienický papier > Utierky papierové  > Skladané ZZ utierky', 1023, 10, 'Utierky - ZZ skladané');

-- Level 2: Podkategórie (priorita 20)
INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Hygienický papier > Toaletný  papier', 101, 20, 'Toaletný papier'),
('humed', 'Hygienický papier > Utierky papierové ', 102, 20, 'Papierové utierky - hlavná'),
('humed', 'Hygienický papier > Papierové servítky', 103, 20, 'Papierové servítky'),
('humed', 'Hygienický papier > Papierové vreckovky, kozmetické utierky', 104, 20, 'Vreckovky a kozmetické utierky'),
('humed', 'Hygienický papier > Zdravotnícke papierové podložky', 105, 20, 'Zdravotnícke podložky'),
('humed', 'Hygienický papier > Hygienické vlhčené utierky, obrúsky ', 10, 20, 'Vlhčené utierky -> Hygienický papier (rodič)');

-- Level 1: Fallback (priorita 50)
INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Hygienický papier', 10, 50, 'Fallback - Hygienický papier');


-- -------------------------------------------------------------
-- 2. MYDLÁ
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Mydlá > Tekuté', 201, 20, 'Tekuté mydlá'),
('humed', 'Mydlá > Tuhé', 202, 20, 'Tuhé mydlá'),
('humed', 'Mydlá > Speňovacie ', 203, 20, 'Speňovacie mydlá'),
('humed', 'Mydlá', 20, 50, 'Fallback - Mydlá');


-- -------------------------------------------------------------
-- 3. OSVIEŽOVAČE VZDUCHU
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Osviežovače vzduchu', 30, 50, 'Osviežovače vzduchu');


-- -------------------------------------------------------------
-- 4. ZÁSOBNÍKY, DÁVKOVAČE A KOŠE
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Zásobníky papierových utierok', 401, 20, 'Zásobníky papierových utierok'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Zásobníky toaletného papiera', 402, 20, 'Zásobníky toaletného papiera'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Dávkovače tekutého mydla', 403, 20, 'Dávkovače tekutého mydla'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Dávkovače mydlovej peny', 404, 20, 'Dávkovače mydlovej peny'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Dávkovače dezinfekcie', 405, 20, 'Dávkovače dezinfekcie'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Koše', 406, 20, 'Koše'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety > Kúpeľňové sety', 407, 20, 'Kúpeľňové sety'),
('humed', 'Zásobníky, dávkovače,  koše, sušiče a kupeľňové sety', 40, 50, 'Fallback - Zásobníky');


-- -------------------------------------------------------------
-- 5. ČISTIACE PROSTRIEDKY
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Čistiace prostriedky  > Kúpeľňa, WC, čističe odpadov', 501, 20, 'Kúpeľňa, WC'),
('humed', 'Čistiace prostriedky  > Umývanie riadu a kuchyňa', 502, 20, 'Kuchyňa a riad'),
('humed', 'Čistiace prostriedky  > Podlaha a univerzál', 503, 20, 'Podlahy'),
('humed', 'Čistiace prostriedky  > Nábytok a okná', 504, 20, 'Nábytok a okná'),
('humed', 'Čistiace prostriedky  > Profi čistiace prostriedky', 505, 20, 'Profi čističe'),
('humed', 'Čistiace prostriedky  > WC bloky', 506, 20, 'WC bloky'),
('humed', 'Čistiace prostriedky  > Vonné sitka do pisoárov ', 507, 20, 'Vonné sitka'),
('humed', 'Čistiace prostriedky  > Čistiace pasty na ruky', 508, 20, 'Pasty na ruky'),
('humed', 'Čistiace prostriedky  > Pohlcovač vlhkosti vzduchu', 509, 20, 'Pohlcovač vlhkosti'),
('humed', 'Čistiace prostriedky ', 50, 50, 'Fallback - Čistiace prostriedky');


-- -------------------------------------------------------------
-- 6. RUKAVICE
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Rukavice jednorázové a upratovacie > Nitrilové rukavice', 601, 20, 'Nitrilové rukavice'),
('humed', 'Rukavice jednorázové a upratovacie > Nitrilové rukavice GOGRIP', 602, 20, 'Nitrilové GOGRIP'),
('humed', 'Rukavice jednorázové a upratovacie > Latexové rukavice', 603, 20, 'Latexové rukavice'),
('humed', 'Rukavice jednorázové a upratovacie > Vinylové rukavice', 604, 20, 'Vinylové rukavice'),
('humed', 'Rukavice jednorázové a upratovacie > Upratovacie rukavice', 605, 20, 'Upratovacie rukavice'),
('humed', 'Rukavice jednorázové a upratovacie > Držiak na rukavice', 606, 20, 'Držiak na rukavice'),
('humed', 'Rukavice jednorázové a upratovacie', 60, 50, 'Fallback - Rukavice');


-- -------------------------------------------------------------
-- 7. UPRATOVACIE POMÔCKY
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Upratovacie pomôcky > Mopy, držiaky mopov a  pady', 701, 20, 'Mopy a držiaky'),
('humed', 'Upratovacie pomôcky > Vedrá, mop sety a upratovacie vozíky', 702, 20, 'Vedrá a vozíky'),
('humed', 'Upratovacie pomôcky > Handry tkané, netkané a mikro', 703, 20, 'Handry a utierky'),
('humed', 'Upratovacie pomôcky > Hubky , drôtenky, špongiové, superstrong a uniabsorb utierky, kefky', 704, 20, 'Hubky a drôtenky'),
('humed', 'Upratovacie pomôcky > Stierky, škrabky, rozmývače a kefy', 705, 20, 'Stierky a škrabky'),
('humed', 'Upratovacie pomôcky > Metly, metličky, lopatky a ometače', 706, 20, 'Metly a lopatky'),
('humed', 'Upratovacie pomôcky > Násady, tyče a teleskopické tyče', 707, 20, 'Tyče a násady'),
('humed', 'Upratovacie pomôcky > WC kefy, WC sety, zvony', 708, 20, 'WC kefy a zvony'),
('humed', 'Upratovacie pomôcky > Vlhčené upratovacie utierky', 709, 20, 'Vlhčené upratovacie utierky'),
('humed', 'Upratovacie pomôcky > Mikro utierky, kuchynské utierky, šnúry, štipce', 710, 20, 'Mikro a kuchynské utierky'),
('humed', 'Upratovacie pomôcky', 70, 50, 'Fallback - Upratovacie pomôcky');


-- -------------------------------------------------------------
-- 8. NETKANÁ TEXTÍLIA
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Netkaná textília - viacúčelové utierky', 80, 50, 'Netkaná textília');


-- -------------------------------------------------------------
-- 9. VRECIA
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'Vrecia > Vrecia 10 L - 35 L', 901, 20, 'Vrecia 10-35L'),
('humed', 'Vrecia > Vrecia 60 L - 90 L', 902, 20, 'Vrecia 60-90L'),
('humed', 'Vrecia > Vrecia 100 L - 120 L', 903, 20, 'Vrecia 100-120L'),
('humed', 'Vrecia > Vrecia 150 L - 660 L', 904, 20, 'Vrecia 150-660L'),
('humed', 'Vrecia', 90, 50, 'Fallback - Vrecia');


-- -------------------------------------------------------------
-- 10. TORK HYGIENA (Rozdelenie)
-- -------------------------------------------------------------

INSERT INTO catalog.category_rules (source, source_category_exact, target_category_id, priority, notes) VALUES
('humed', 'TORK hygiena > Hygienický papier, utierky', 10, 30, 'TORK -> Hygienický papier'),
('humed', 'TORK hygiena > Mydlá, osviežovače vzduchu', 20, 30, 'TORK -> Mydlá'),
('humed', 'TORK hygiena > Zásobníky a dávkovače', 40, 30, 'TORK -> Zásobníky'),
('humed', 'TORK hygiena', 10, 50, 'TORK fallback -> Hygienický papier');


-- =============================================================
-- PRICE RULES: Pravidlá pre výpočet B2B cien
-- =============================================================

-- Globálne pravidlo: 55% marža na všetko
INSERT INTO catalog.price_rules (category_id, source, rule_type, value, priority, is_active) VALUES
(NULL, NULL, 'margin_percent', 55.00, 100, TRUE);

-- Špecifické pravidlo pre TORK (môže mať nižšiu maržu)
-- INSERT INTO catalog.price_rules (category_id, source, rule_type, value, priority, is_active) VALUES
-- (NULL, 'humed', 'margin_percent', 50.00, 50, TRUE);


-- =============================================================
-- VIEWS: Pomocné pohľady
-- =============================================================

-- Pohľad na kategórie s cestou
CREATE OR REPLACE VIEW catalog.v_categories_with_path AS
SELECT 
    c.id,
    c.parent_id,
    c.level,
    c.name,
    c.slug,
    c.menu_name,
    c.h1_seo_name,
    c.path::text as ltree_path,
    (
        SELECT string_agg(p.name, ' > ' ORDER BY p.level)
        FROM catalog.categories p
        WHERE c.path <@ p.path OR c.id = p.id
    ) as full_path,
    c.is_active
FROM catalog.categories c;

-- Pohľad na nenamapované produkty
CREATE OR REPLACE VIEW catalog.v_unmapped_products AS
SELECT 
    h.feed_id,
    h.sku,
    h.title,
    h.categories,
    h.imported_at
FROM staging.humed_raw h
WHERE NOT EXISTS (
    SELECT 1 FROM catalog.category_mapping_log m 
    WHERE m.source = 'humed' 
    AND m.source_product_id = h.feed_id
    AND m.match_type != 'unmapped'
)
AND h.is_excluded = FALSE;

-- Pohľad na štatistiky mapovania
CREATE OR REPLACE VIEW catalog.v_mapping_stats AS
SELECT 
    source,
    match_type,
    COUNT(*) as count,
    COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (PARTITION BY source) as percentage
FROM catalog.category_mapping_log
GROUP BY source, match_type
ORDER BY source, count DESC;
