package sk.pcola.etl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.slugify.Slugify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import sk.pcola.etl.dto.EnrichedProductData;
import sk.pcola.etl.dto.OptimizedProductDto;
import sk.pcola.etl.strategy.ProductStrategy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ProductOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(ProductOptimizationService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private static final Set<String> IGNORED_BRANDS = Set.of(
            "LDPE", "HDPE", "PP", "PET", "PVC", "BIO", "EKO", "ECO", "RECYKLÁT", "ALU", "PE", "PS", "XPS", "TETRAPAK",
            "XXL", "XL", "L", "M", "S", "XS", "KS", "MJ", "BAL", "KARTÓN", "ROLA", "ML", "KG", "CM", "MM", "MY", "MIC",
            "GR", "G",
            "PROFI", "SUPER", "MAXI", "MINI", "SET", "NOVINKA", "AKCIA", "VÝHODNÉ", "PREMIUM", "QUALITY", "CLASSIC",
            "ECONOMY", "STANDARD", "EXTRA", "PLUS", "UNIVERSAL", "EXPERT", "POWER", "TURBO", "GIGA", "MEGA",
            "WC", "PH", "HACCP", "ISO", "DIN", "EN",
            "ZZ", "V", "C", "Z", "T1", "T2", "T3", "T4", "H1", "H2", "H3", "S1", "S2", "S4", "M1", "M2", "A1", "A2",
            "B1", "B2", "B3");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final StrategyClassifier classifier;
    private final ManufacturerEnrichmentService enrichmentService;
    private final Slugify slugify = Slugify.builder().build();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.enrichment.enabled:true}")
    private boolean enrichmentEnabled;

    public ProductOptimizationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            StrategyClassifier classifier, ManufacturerEnrichmentService enrichmentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.classifier = classifier;
        this.enrichmentService = enrichmentService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    }

    public void runOptimizationPipeline() {
        while (true) {
            List<ProductToOptimize> products = loadPendingProducts();
            if (products.isEmpty()) {
                log.info("Žiadne ďalšie pending produkty. Pipeline končí.");
                break;
            }

            log.info("Spúšťam Moonid B2B Pipeline (CamelCase Fix) pre {} produktov.", products.size());

            for (ProductToOptimize product : products) {
                try {
                    processSingleProduct(product);
                } catch (Exception e) {
                    log.error("Chyba pri SKU {}: {}", product.sku(), e.getMessage());
                    markAsFailed(product.id(), e.getMessage());
                }
            }
        }
    }

    private void processSingleProduct(ProductToOptimize product) throws Exception {
        markAsProcessing(product.id());

        ProductStrategy strategy = classifier.detect(product.category(), product.name());
        log.info("Produkt {}: Stratégia {}", product.sku(), strategy);

        // === MANUFACTURER ENRICHMENT ===
        // Pre značkové produkty (TORK, KATRIN, KIMBERLY...) vyhľadaj dáta z oficiálnej
        // stránky výrobcu
        EnrichedProductData enrichedData = EnrichedProductData.empty();
        Optional<String> detectedBrand = Optional.empty();

        if (enrichmentEnabled) {
            detectedBrand = enrichmentService.detectBrand(product.name());
            if (detectedBrand.isPresent()) {
                log.info("Produkt {} je značka {}, spúšťam manufacturer enrichment",
                        product.sku(), detectedBrand.get());
                enrichedData = enrichmentService.enrichFromManufacturer(
                        product.sku(), detectedBrand.get(), product.name());

                if (enrichedData.hasValidEan()) {
                    log.info("Nájdený EAN {} pre SKU {} (confidence: {})",
                            enrichedData.eanGtin(), product.sku(), enrichedData.confidenceScore());
                }
            }
        }

        // Prompt s enriched dátami ak existujú
        String prompt = buildStrictB2BPrompt(product, strategy, enrichedData);
        String jsonResponse = callGeminiApi(prompt);

        String contentText;
        try {
            contentText = extractJsonFromResponse(jsonResponse, product.sku());
        } catch (Exception e) {
            markAsFailed(product.id(), "Chybný JSON z Gemini: " + e.getMessage());
            return;
        }

        OptimizedProductDto dto = objectMapper.readValue(contentText, OptimizedProductDto.class);

        dto = normalizeDto(dto, product);

        String groupingKey = slugify.slugify(dto.parentProductName());
        if (groupingKey.isBlank())
            groupingKey = "sku-" + product.sku().toLowerCase();

        ScoreResult qualityCheck = computeWeightedScore(dto.specs(), strategy);
        String finalHtml = assembleDynamicHtml(dto, strategy);

        // Uloží aj enriched dáta ak existujú
        saveToDatabase(product, dto, groupingKey, finalHtml, qualityCheck, strategy, enrichedData);
        log.info("SKU {}: OK (Skóre: {}%{})", product.sku(), qualityCheck.score(),
                enrichedData.hasValidEan() ? ", EAN: " + enrichedData.eanGtin() : "");
    }

    // === 1. PROMPT (B2B OPTIMALIZOVANÝ PRE SEO/AEO/GEO/LLM) ===
    // VERZIA 3.0: Rozšírené o enriched dáta z oficiálnych stránok výrobcov
    private String buildStrictB2BPrompt(ProductToOptimize product, ProductStrategy strategy,
            EnrichedProductData enrichedData) {
        String systemInstruction = "";
        if (strategy == ProductStrategy.PAPER_HYGIENE || strategy == ProductStrategy.DISPENSERS_AND_BINS
                || strategy == ProductStrategy.AIR_CARE) {
            systemInstruction = "KRITICKÉ: Uveď kompatibilný SYSTÉM (Tork T1, Katrin H2, atď.) ak existuje!";
        }

        String cleanInputDesc = aggressiveClean(product.description());

        // Príprava enriched dát pre prompt
        StringBuilder enrichedSection = new StringBuilder();
        if (enrichedData != null && !enrichedData.equals(EnrichedProductData.empty())) {
            enrichedSection.append("\n=== OVERENÉ DÁTA Z OFICIÁLNEJ STRÁNKY VÝROBCU ===\n");

            if (enrichedData.hasValidEan()) {
                enrichedSection.append("EAN/GTIN: ").append(enrichedData.eanGtin()).append(" (OVERENÝ - použiť!)\n");
            }

            if (enrichedData.manufacturerSku() != null && !enrichedData.manufacturerSku().isBlank()) {
                enrichedSection.append("SKU výrobcu: ").append(enrichedData.manufacturerSku()).append("\n");
            }

            if (enrichedData.specs() != null && !enrichedData.specs().isEmpty()) {
                enrichedSection.append("Technické parametre z výrobcu:\n");
                enrichedData.specs().forEach(
                        (k, v) -> enrichedSection.append("  - ").append(k).append(": ").append(v).append("\n"));
            }

            if (enrichedData.certifications() != null && !enrichedData.certifications().isEmpty()) {
                enrichedSection.append("Certifikácie: ").append(String.join(", ", enrichedData.certifications()))
                        .append("\n");
            }

            if (enrichedData.detailedDescription() != null && !enrichedData.detailedDescription().isBlank()) {
                enrichedSection.append("Oficiálny popis výrobcu:\n").append(enrichedData.detailedDescription())
                        .append("\n");
            }

            enrichedSection
                    .append("KRITICKÉ: Tieto údaje sú z oficiálnej stránky výrobcu - majú PRIORITU pred feedom!\n");
        }

        return String.format(
                """
                        Si B2B produktový špecialista pre Moonid.sk (slovenský veľkoobchod s hygienou a upratovacími potrebami).
                        CIEĽOVÁ SKUPINA: Nákupcovia firiem, hotelov, reštaurácií, úradov, nemocníc.
                        Stratégia: %s
                        %s
                        === VSTUPNÉ DÁTA ===
                        SKU: %s
                        Názov: %s
                        Popis: %s

                        === KRITICKÉ PRAVIDLÁ ===
                        1. Píš FAKTY, nie marketingové frázy. B2B zákazník vie čo chce.
                        2. ZAKÁZANÉ frázy: "vysoká kvalita", "profesionálne použitie", "štandardy kvality", "ideálny pre", "perfektný"
                        3. Každá sekcia musí mať ODLIŠNÝ obsah!
                        4. Všetky texty musia byť UNIKÁTNE - NEKOPÍRUJ vstup!
                        5. AK SÚ DOSTUPNÉ OVERENÉ DÁTA Z VÝROBCU, POUŽI ICH!

                        === EXTRAKCIA PARAMETROV (specs) ===
                        %s
                        %s
                        POVINNÉ parametre: %s
                        PLUS extrahuj ak sú dostupné:
                        - "balenie_karton": počet kusov v kartóne
                        - "ean": čiarový kód (EAN-13, 13 číslic) ak je v popise ALEBO z overených dát
                        - "certifikacie": HACCP, ISO, EN normy (DÔLEŽITÉ pre B2B!)

                        === FOCUS KEYWORD (B2B LONG-TAIL!) ===
                        suggestedFocusKeyword MUSÍ obsahovať:
                        - Typ produktu + objem/veľkosť + "veľkoobchod" alebo "B2B" alebo "pre firmy"
                        - Príklad SPRÁVNE: "nitrilové rukavice L 100ks veľkoobchod"
                        - Príklad ZLE: "rukavice modré" (príliš generické)

                        === META TITLE (KRITICKÉ: 30-40 znakov, suffix pridám ja!) ===
                        MUSÍ obsahovať: [Značka] [Produkt] [Veľkosť/Objem] [Variant]
                        Príklad SPRÁVNY (38 zn.): "LEWI Držiak Rozmývača ALU 25cm Modrý"
                        Príklad SPRÁVNY (35 zn.): "Nitrilové Rukavice NITRYLEX L 100ks"
                        Príklad SPRÁVNY (32 zn.): "Tekuté Mydlo VIONE Antibak 1000ml"
                        Príklad ZLÝ (18 zn.): "LEWI Držiak 25cm" - PRÍLIŠ KRÁTKE!
                        ZAKÁZANÉ: generické názvy bez špecifikácie veľkosti/objemu

                        === META DESCRIPTION (B2B ŠTÝL, 120-155 znakov POVINNE!) ===
                        KRITICKÉ: Musí mať 120-155 znakov! Kratšie = nevyužitý priestor v Google SERP.
                        Formát: [Produkt] [objem] pre [segment] | [Vlastnosť 1] | [Vlastnosť 2] | Balenie Xks | Dodanie 2-3 dni | Objednajte veľkoobchodne.
                        Príklad SPRÁVNY (128 zn.): "Tekuté mydlo VIONE 1l pre hotely a kancelárie | pH neutrálne | Dermatologicky testované | Balenie 12ks | Dodanie 2-3 dni."
                        Príklad ZLÝ (67 zn.): "Tekuté mydlo VIONE 1l | pH neutrálne | Dodanie 2-3 dni." - PRÍLIŠ KRÁTKE!
                        ZAKÁZANÉ: emocionálny jazyk, "ideálny pre", "perfektný", prídavné mená bez hodnoty

                        === SEKCIE (ROZŠÍRENÉ PRE 300+ SLOV!) ===

                        sectionProblem (3-5 viet, min 50 slov) - TECHNICKÝ KONTEXT A POŽIADAVKY ODVETVIA:
                        - Uveď kde/kedy sa produkt používa a aké požiadavky musí spĺňať
                        - Spomeň legislatívne požiadavky, normy, hygienické štandardy ak relevantné
                        - Príklad: "Gastro prevádzky podliehajú prísnym hygienickým normám HACCP. Utierky musia byť odolné voči tukom, čistiacim prostriedkom a vysokým teplotám. Pri manipulácii s potravinami je kritická absencia uvoľňovania vlákien."
                        - ZAKÁZANÉ: otázky ("Hľadáte...?"), marketingové frázy

                        sectionSolution (4-6 viet, min 80 slov) - TECHNICKÉ VLASTNOSTI A VÝHODY:
                        - Konkrétne vlastnosti TOHTO produktu (materiál, zloženie, technológia)
                        - Uveď merateľné parametre (gramáž, absorpcia, pH, koncentrácia)
                        - Porovnaj s alternatívami ak relevantné
                        - Príklad: "Netkaná textília 70g/m² z 80%% viskózy a 20%% polyesteru. Absorbuje 8x vlastnú hmotnosť tekutín vrátane olejov. Nerozpadá sa pri mokrom použití ani pri teplotách do 60°C. Perforované balenie umožňuje hygienické vyberanie jednotlivých utierok. Na rozdiel od papierových utierok nezanechávajú vlákna na povrchoch."
                        - ZAKÁZANÉ: všeobecné tvrdenia, duplicita so sectionProblem

                        sectionUsage (3-4 vety, min 40 slov) - PRAKTICKÝ NÁVOD:
                        - Praktické použitie, riedenie, dávkovanie ak relevantné
                        - Odporúčané kombinácie s inými produktmi
                        - Skladovanie a bezpečnostné upozornenia
                        - Príklad: "Používajte jednorazovo pre maximálnu hygienu. Pri silnom znečistení navlhčite čistiacim roztokom. Skladujte v suchom prostredí, chráňte pred priamym slnečným svetlom. Kompatibilné so zásobníkmi Katrin a Tork."

                        sectionApplications (2-3 vety) - ODVETVIA A POUŽITIE:
                        - Konkrétne odvetvia kde sa produkt používa
                        - Príklad: "Vhodné pre reštaurácie, hotelové kuchyne, nemocničné jedálne, školské stravovacie zariadenia."

                        sectionAdvantages (3-4 body) - KĽÚČOVÉ VÝHODY PRE B2B:
                        - Úspora nákladov, efektivita, jednoduchá manipulácia
                        - Príklad: "Nižšie náklady na jednotku vďaka veľkoobchodnému baleniu. Menšia spotreba vďaka vysokej absorpcii. Menej odpadu - jedna utierka nahrádza 3 papierové."

                        === FAQ (KRITICKÉ: 4-6 B2B OTÁZOK!) ===
                        Generuj PRESNE 5 FAQ ktoré by sa B2B zákazník pýtal:
                        1. "Koľko kusov je v kartóne a aká je minimálna objednávka?"
                        2. "Je produkt kompatibilný so zásobníkom [systém]?" (ak relevantné)
                        3. "Aká je životnosť/trvanlivosť produktu?"
                        4. "Máte kartu bezpečnostných údajov (KBÚ)?" (pre chemikálie)
                        5. "Aké certifikácie má produkt?" (HACCP, ISO, EN normy)
                        6. "Je možné objednať vzorku pred väčšou objednávkou?"
                        ZAKÁZANÉ: triviálne otázky ("Je to kvalitné?", "Funguje to?")

                        === CERTIFIKÁCIE (DÔLEŽITÉ PRE B2B!) ===
                        Aktívne hľadaj a extrahuj certifikácie z popisu:
                        - HACCP, ISO 9001, ISO 14001, ISO 22716
                        - EN normy (EN 455, EN 374, EN 388, EN 14476, EN 1276, EN 1499, EN 13727)
                        - Biocídne povolenia, dermatologické testy, CE značky
                        Ak žiadne nie sú zmienené, vráť prázdny zoznam [].

                        === TARGET SEGMENTS (POVINNÉ!) ===
                        Vyber 2-4 najrelevantnejšie segmenty:
                        - hotely, kancelárie, gastro, nemocnice, školy, výroba, maloobchod, logistika, wellness

                        === JSON OUTPUT (camelCase) ===
                        {
                          "parentProductName": "Názov bez varianty (30-50 znakov)",
                          "brandDetected": "Značka alebo Generic",
                          "suggestedFocusKeyword": "B2B long-tail keyword s veľkoobchod/pre firmy",
                          "searchIntent": "transactional",
                          "nameH1": "Kompletný názov produktu s veľkosťou (30-60 znakov)",
                          "metaTitle": "30-40 znakov: Značka + Produkt + Veľkosť",
                          "metaDescription": "120-155 znakov: Produkt | Vlastnosti | Balenie | Dodanie",
                          "shortDescription": "2-3 faktické vety (40-60 slov)",
                          "sectionProblem": "3-5 viet o kontexte použitia (min 50 slov)",
                          "sectionSolution": "4-6 viet o vlastnostiach produktu (min 80 slov)",
                          "sectionUsage": "3-4 vety návod na použitie (min 40 slov)",
                          "sectionApplications": "2-3 vety o odvetviach (min 20 slov)",
                          "sectionAdvantages": "3-4 body výhod pre B2B",
                          "imageAltText": "Popis obrázka pre alt text",
                          "specs": {
                            "objem": "hodnota s jednotkou",
                            "balenie_karton": "X ks",
                            "ean": "EAN-13 čiarový kód ak dostupný",
                            "certifikacie": "zoznam certifikácií"
                          },
                          "faq": [
                            { "name": "B2B otázka 1?", "acceptedAnswer": { "text": "Faktická odpoveď min 20 slov." } },
                            { "name": "B2B otázka 2?", "acceptedAnswer": { "text": "Faktická odpoveď min 20 slov." } },
                            { "name": "B2B otázka 3?", "acceptedAnswer": { "text": "Faktická odpoveď min 20 slov." } },
                            { "name": "B2B otázka 4?", "acceptedAnswer": { "text": "Faktická odpoveď min 20 slov." } },
                            { "name": "B2B otázka 5?", "acceptedAnswer": { "text": "Faktická odpoveď min 20 slov." } }
                          ],
                          "features": ["Kľúčová vlastnosť 1", "Kľúčová vlastnosť 2", "Kľúčová vlastnosť 3", "Kľúčová vlastnosť 4"],
                          "targetSegments": ["segment1", "segment2", "segment3"],
                          "certifications": ["HACCP", "ISO 9001", "EN norma"]
                        }
                        """,
                strategy.name(),
                enrichedSection.toString(),
                product.sku(), product.name(), cleanInputDesc,
                strategy.getPromptContext(),
                systemInstruction,
                String.join(", ", strategy.getRequiredSpecs()));
    }

    // === 2. NORMALIZÁCIA (FIXED: smart truncate, no generic fallback,
    // differentiated sections) ===
    private OptimizedProductDto normalizeDto(OptimizedProductDto dto, ProductToOptimize product) {
        String detectedBrand = dto.brandDetected();
        String firstWord = product.name().split("\\s+")[0];
        if ((detectedBrand == null || "Generic".equalsIgnoreCase(detectedBrand.trim())) && isLikelyBrand(firstWord)) {
            detectedBrand = firstWord.replaceAll("[^a-zA-Z0-9!]", "");
        }
        String brand = nonNull(detectedBrand, "Generic");

        String nameH1 = nonNull(dto.nameH1(), titleCase(product.name()));
        String parentName = nonNull(dto.parentProductName(), nameH1);
        String originalDescClean = aggressiveClean(product.description());

        String metaDesc = smartTruncate(nonNull(dto.metaDescription(), originalDescClean), 150);

        // FIXED: Rozšírená detekcia B2C/garbage fráz
        String prob = nonNull(dto.sectionProblem(), "");
        boolean probIsGarbage = prob.isBlank() || prob.length() < 15
                || prob.equalsIgnoreCase(originalDescClean)
                || containsGarbagePhrase(prob);

        String sol = nonNull(dto.sectionSolution(), "");
        boolean solIsGarbage = sol.isBlank() || sol.length() < 15
                || sol.equalsIgnoreCase(originalDescClean)
                || containsGarbagePhrase(sol);

        // Ak obe sekcie zlyhali, vytvoríme diferencovaný obsah z názvu produktu
        if (probIsGarbage && solIsGarbage) {
            prob = ""; // Radšej prázdne ako duplicitné
            sol = "";
        } else if (probIsGarbage) {
            prob = ""; // Nechaj prázdne, solution má obsah
        } else if (solIsGarbage) {
            sol = ""; // Nechaj prázdne, problem má obsah
        }

        // Ak sú IDENTICKÉ (Gemini lenivosť), vymažeme jednu
        if (!prob.isBlank() && prob.equals(sol)) {
            sol = "";
        }

        String keyword = dto.suggestedFocusKeyword();
        if (keyword == null || keyword.isBlank() || keyword.length() < 3) {
            keyword = generateFocusKeyword(nameH1);
        }

        // FIXED: Meta title - suffix " | Moonid" musí byť vždy zachovaný
        String metaTitle = buildMetaTitle(dto.metaTitle(), nameH1, 60);

        // B2B signály - extrahuj z specs ak Gemini ich tam dal
        Map<String, String> specs = sanitizeSpecs(dto.specs());

        // Nové sekcie pre rozšírený obsah (300+ slov)
        String applications = nonNull(dto.sectionApplications(), "");
        String advantages = nonNull(dto.sectionAdvantages(), "");

        return new OptimizedProductDto(
                smartTruncate(parentName, 120),
                smartTruncate(brand, 50),
                smartTruncate(keyword, 60),
                "transactional",
                smartTruncate(nameH1, 90),
                metaTitle,
                metaDesc,
                smartTruncate(nonNull(dto.shortDescription(), originalDescClean), 250),
                prob,
                sol,
                smartTruncate(nonNull(dto.sectionUsage(), ""), 500),
                smartTruncate(applications, 300), // Odvetvia použitia
                smartTruncate(advantages, 400), // Výhody pre B2B
                smartTruncate(nonNull(dto.imageAltText(), nameH1), 100),
                specs,
                sanitizeFaq(dto.faq()),
                sanitizeFeatures(dto.features()),
                sanitizeList(dto.targetSegments()),
                sanitizeList(dto.certifications()));
    }

    // ... Zvyšok metód (aggressiveClean, smartTruncate, generateFocusKeyword,
    // helpers...) ostáva rovnaký ako vo V018 ...
    // PRE ÚPLNOSŤ ICH TU UVÁDZAM SKRÁTENE:

    private String aggressiveClean(String d) {
        if (d == null)
            return "";
        return d.replaceAll("(?i)(cena|minimálny|odber|balenie/mj).*", "").replaceAll("(?i)mj\\s*\\d+.*", "")
                .replaceAll("(?i)rozmer\\s*\\d+x\\d+.*", "").replaceAll("(?i)bezpečnostné upozornenie:.*", "")
                .replaceAll("(?i)nebezpečenstvo.*", "").replaceAll("[\\h\\t\\u00A0]+", " ").trim();
    }

    // B2C/Garbage frázy ktoré nechceme v B2B obsahu
    private static final List<String> GARBAGE_PHRASES = List.of(
            "štandardy kvality", "profesionálne použitie", "vysoká kvalita",
            "ideálny pre", "perfektný pre", "skvelý pre", "výborný pre",
            "hľadáte", "potrebujete", "chcete",
            "najlepší výber", "správna voľba", "spoľahlivý partner",
            "vďaka ktorému", "s ktorým", "pomocou ktorého");

    private boolean containsGarbagePhrase(String text) {
        if (text == null)
            return true;
        String lower = text.toLowerCase();
        return GARBAGE_PHRASES.stream().anyMatch(lower::contains);
    }

    // FIXED: Meta title s B2B signálom - "Veľkoobchod" pre SEO
    private String buildMetaTitle(String geminiTitle, String nameH1, int maxLen) {
        String suffix = " | Veľkoobchod Moonid"; // B2B signál!
        int suffixLen = suffix.length(); // 20 znakov
        int availableForName = maxLen - suffixLen; // 40 znakov pre názov

        String baseName;
        if (geminiTitle != null && !geminiTitle.isBlank()) {
            // Odstráň akýkoľvek existujúci suffix
            baseName = geminiTitle
                    .replace(" | Moonid", "")
                    .replace(" | Veľkoobchod Moonid", "")
                    .replace("| Moonid", "")
                    .trim();
        } else {
            baseName = nameH1;
        }

        return smartTruncate(baseName, availableForName) + suffix;
    }

    private String smartTruncate(String t, int m) {
        if (t == null)
            return "";
        t = t.trim();
        if (t.length() <= m)
            return t;
        String s = t.substring(0, m);
        int d = Math.max(s.lastIndexOf("."), Math.max(s.lastIndexOf("!"), s.lastIndexOf("?")));
        if (d > m * 0.4)
            return s.substring(0, d + 1);
        int sp = s.lastIndexOf(" ");
        return sp > 0 ? s.substring(0, sp) + "..." : s + "...";
    }

    // FIXED: Regex now includes Slovak characters (uppercase + lowercase)
    private String generateFocusKeyword(String n) {
        if (n == null)
            return "";
        return n.toLowerCase().replaceAll("[^a-záäčďéíľĺňóôŕšťúýžA-ZÁÄČĎÉÍĽĹŇÓÔŔŠŤÚÝŽ0-9 ]", "").replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isLikelyBrand(String w) {
        if (w == null || w.length() < 2)
            return false;
        String c = w.replaceAll("[^a-zA-Z0-9]", "");
        if (c.isEmpty())
            return false;
        if (IGNORED_BRANDS.contains(c.toUpperCase()))
            return false;
        return c.matches(".*[A-Z].*") && c.equals(c.toUpperCase());
    }

    private String nonNull(String s, String f) {
        return (s == null || s.isBlank()) ? f : s;
    }

    private String titleCase(String i) {
        if (i == null)
            return "";
        String[] w = i.toLowerCase().split("\\s+");
        StringBuilder s = new StringBuilder();
        for (String word : w)
            if (!word.isBlank())
                s.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        return s.toString().trim();
    }

    // FIXED: Zvýšená teplota z 0.5 na 0.75 pre kreatívnejšie odpovede
    private String callGeminiApi(String p) throws Exception {
        Map<String, Object> r = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", p)))),
                "generationConfig", Map.of("temperature", 0.75, "maxOutputTokens", 2000));
        String b = objectMapper.writeValueAsString(r);
        int max = 3, att = 0;
        while (true) {
            att++;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(GEMINI_API_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(b, StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200)
                return res.body();
            if (res.statusCode() == 429 && att < max) {
                Thread.sleep(2000);
                continue;
            }
            throw new RuntimeException("API " + res.statusCode() + ": " + res.body());
        }
    }

    private String extractJsonFromResponse(String r, String s) throws Exception {
        JsonNode root = objectMapper.readTree(r);
        JsonNode c = root.path("candidates");
        if (c.isEmpty())
            throw new RuntimeException("No candidates");
        String t = c.get(0).path("content").path("parts").get(0).path("text").asText().trim();
        if (t.startsWith("```json"))
            t = t.substring(7).trim();
        if (t.startsWith("```"))
            t = t.substring(3).trim();
        if (t.endsWith("```"))
            t = t.substring(0, t.length() - 3).trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start == -1 || end == -1)
            throw new RuntimeException("No JSON");
        return t.substring(start, end + 1);
    }

    // ROZŠÍRENÝ HTML pre 300+ slov
    private String assembleDynamicHtml(OptimizedProductDto dto, ProductStrategy strategy) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='b2b-product-detail ").append(strategy.name().toLowerCase()).append("'>");

        // 1. Technický kontext a riešenie
        appendSectionIfContentExists(sb, "technical-context", "Oblasť použitia a technické riešenie",
                dto.sectionProblem(), dto.sectionSolution());

        // 2. Návod na použitie
        appendSectionIfContentExists(sb, "application-guide", "Návod na použitie",
                dto.sectionUsage(), null);

        // 3. NOVÉ: Odvetvia a oblasti použitia
        if (dto.sectionApplications() != null && !dto.sectionApplications().isBlank()) {
            sb.append("<section class='applications mb-3'><h3>Odvetvia a oblasti použitia</h3>");
            sb.append("<p>").append(dto.sectionApplications()).append("</p>");
            sb.append("</section>");
        }

        // 4. NOVÉ: Výhody pre B2B
        if (dto.sectionAdvantages() != null && !dto.sectionAdvantages().isBlank()) {
            sb.append("<section class='b2b-advantages mb-3'><h3>Výhody pre vašu prevádzku</h3>");
            sb.append("<p>").append(dto.sectionAdvantages()).append("</p>");
            sb.append("</section>");
        }

        // 5. Technické parametre
        sb.append("<section class='technical-specs'><h3>Technické parametre</h3>");
        if (dto.specs() != null && !dto.specs().isEmpty()) {
            sb.append("<table class='table table-striped table-sm'>");
            dto.specs().forEach((k, v) -> sb.append("<tr><th>").append(capitalize(k))
                    .append("</th><td>").append(v).append("</td></tr>"));
            sb.append("</table>");
        } else {
            sb.append("<p class='text-muted'><em>Technické parametre nie sú k dispozícii.</em></p>");
        }
        sb.append("</section>");

        // 6. Bezpečnostné upozornenie ak relevantné
        if (!strategy.getSafetyWarning().isEmpty()) {
            sb.append("<section class='safety-notice alert alert-warning mt-3'>");
            sb.append("<strong>⚠ BEZPEČNOSŤ:</strong> ").append(strategy.getSafetyWarning());
            sb.append("</section>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private void saveToDatabase(ProductToOptimize product, OptimizedProductDto dto, String gKey, String html,
            ScoreResult score, ProductStrategy strategy, EnrichedProductData enrichedData) {
        try {
            Map<String, String> specs = dto.specs() != null ? dto.specs() : Map.of();

            // PRIORITA EAN:
            // 1. Enriched dáta z oficiálnej stránky výrobcu (najspoľahlivejšie)
            // 2. HUMED feed (ak je validný EAN-13)
            // 3. Gemini extracted z popisu
            String eanGtin = null;
            String enrichmentSource = "humed_feed";
            Double enrichmentConfidence = null;
            String manufacturerUrl = null;
            String groundingSources = null;

            // 1. Skús enriched dáta
            if (enrichedData != null && enrichedData.hasValidEan()) {
                eanGtin = enrichedData.eanGtin();
                enrichmentSource = "manufacturer_website";
                enrichmentConfidence = enrichedData.confidenceScore();
                manufacturerUrl = enrichedData.productUrl();

                if (enrichedData.sourceUrls() != null && !enrichedData.sourceUrls().isEmpty()) {
                    groundingSources = objectMapper.writeValueAsString(enrichedData.sourceUrls());
                }

                log.debug("EAN {} z oficiálnej stránky výrobcu (confidence: {})", eanGtin, enrichmentConfidence);
            }

            // 2. Fallback na HUMED feed
            if (eanGtin == null) {
                String humedGtin = product.gtin();
                if (humedGtin != null && isValidEan(humedGtin)) {
                    eanGtin = humedGtin;
                    enrichmentSource = "humed_feed";
                }
            }

            // 3. Fallback na Gemini extracted
            if (eanGtin == null) {
                String geminiEan = specs.getOrDefault("ean", specs.get("ean_gtin"));
                if (geminiEan != null && isValidEan(geminiEan)) {
                    eanGtin = geminiEan;
                    enrichmentSource = "gemini_inferred";
                    enrichmentConfidence = 0.5; // Nižšia spoľahlivosť pre inferované
                }
            }

            // Merge specs z enrichmentu ak existujú
            Map<String, String> mergedSpecs = new LinkedHashMap<>(specs);
            if (enrichedData != null && enrichedData.specs() != null) {
                enrichedData.specs().forEach((k, v) -> {
                    // Pridaj len ak specs neobsahuje tento kľúč alebo je prázdny
                    if (!mergedSpecs.containsKey(k) || mergedSpecs.get(k) == null || mergedSpecs.get(k).isBlank()) {
                        mergedSpecs.put(k, v);
                    }
                });
            }

            // Merge certifikácie
            List<String> mergedCertifications = new ArrayList<>();
            if (dto.certifications() != null) {
                mergedCertifications.addAll(dto.certifications());
            }
            if (enrichedData != null && enrichedData.certifications() != null) {
                for (String cert : enrichedData.certifications()) {
                    if (!mergedCertifications.contains(cert)) {
                        mergedCertifications.add(cert);
                    }
                }
            }

            Integer packagingQty = product.packagingQty();
            if (packagingQty == null) {
                packagingQty = parsePackagingQuantity(
                        mergedSpecs.getOrDefault("balenie_karton", mergedSpecs.get("balenie")));
            }

            // MOQ = packaging_quantity (B2B predaj po kartónoch), fallback na 1
            Integer moq = packagingQty != null ? packagingQty : 1;

            // Pallet quantity z HUMED feedu
            Integer palletQty = product.palletQty();

            String sql = """
                    UPDATE catalog.products_optimized SET
                        strategy_type=?, parent_product_name=?, grouping_key=?, brand_detected=?,
                        suggested_focus_keyword=?, search_intent=?, name_h1=?, meta_title=?,
                        meta_description=?, short_description=?, html_final=?, image_alt_text=?,
                        section_problem=?, section_solution=?, section_usage=?,
                        json_specs=?::jsonb, json_faq=?::jsonb, json_features=?::jsonb,
                        target_segments=?::jsonb, certifications=?::jsonb,
                        ean_gtin=?, packaging_quantity=?, pallet_quantity=?, moq=?,
                        data_quality_score=?, missing_critical_specs=?::jsonb,
                        enrichment_source=?, enrichment_confidence=?, manufacturer_url=?, grounding_sources=?::jsonb,
                        status='completed', processed_at=NOW(), updated_at=NOW()
                    WHERE id=?
                    """;

            jdbcTemplate.update(sql,
                    strategy.name(), dto.parentProductName(), gKey, dto.brandDetected(),
                    dto.suggestedFocusKeyword(), dto.searchIntent(), dto.nameH1(), dto.metaTitle(),
                    dto.metaDescription(), dto.shortDescription(), html, dto.imageAltText(),
                    dto.sectionProblem(), dto.sectionSolution(), dto.sectionUsage(),
                    objectMapper.writeValueAsString(mergedSpecs),
                    objectMapper.writeValueAsString(dto.faq()),
                    objectMapper.writeValueAsString(dto.features()),
                    objectMapper.writeValueAsString(dto.targetSegments()),
                    objectMapper.writeValueAsString(mergedCertifications),
                    eanGtin, packagingQty, palletQty, moq,
                    score.score(), objectMapper.writeValueAsString(score.missingFields()),
                    enrichmentSource, enrichmentConfidence, manufacturerUrl, groundingSources,
                    product.id());
        } catch (Exception e) {
            throw new RuntimeException("DB Save", e);
        }
    }

    private Integer parsePackagingQuantity(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Validácia EAN kódu (EAN-8, EAN-13, UPC-A)
    private boolean isValidEan(String ean) {
        if (ean == null || ean.isBlank())
            return false;
        String cleaned = ean.replaceAll("[^0-9]", "");
        // EAN-8 (8 číslic), EAN-13 (13 číslic), UPC-A (12 číslic)
        return cleaned.length() == 8 || cleaned.length() == 12 || cleaned.length() == 13;
    }

    private ScoreResult computeWeightedScore(Map<String, String> s, ProductStrategy st) {
        Map<String, Integer> w = st.getSpecWeights();
        int t = 0, p = 0;
        List<String> m = new ArrayList<>();
        for (Map.Entry<String, Integer> e : w.entrySet()) {
            t += e.getValue();
            if (s != null && s.containsKey(e.getKey()) && s.get(e.getKey()) != null && !s.get(e.getKey()).isBlank())
                p += e.getValue();
            else
                m.add(e.getKey());
        }
        return new ScoreResult(t == 0 ? 0 : (int) ((double) p / t * 100), m);
    }

    private Map<String, String> sanitizeSpecs(Map<String, String> s) {
        if (s == null)
            return Map.of();
        Map<String, String> o = new LinkedHashMap<>();
        s.forEach((k, v) -> {
            if (k != null && v != null && !k.isBlank() && !v.isBlank())
                o.put(smartTruncate(k, 60), smartTruncate(v, 200));
        });
        return o;
    }

    // FIXED: Doplnenie @type pre Google structured data (FAQPage schema)
    private List<OptimizedProductDto.FaqItem> sanitizeFaq(List<OptimizedProductDto.FaqItem> f) {
        if (f == null || f.isEmpty())
            return List.of();
        return f.stream()
                .filter(item -> item.question() != null && !item.question().isBlank())
                .map(item -> new OptimizedProductDto.FaqItem(
                        "Question", // @type MUSÍ byť "Question"
                        item.question(),
                        new OptimizedProductDto.FaqItem.AcceptedAnswer(
                                "Answer", // @type MUSÍ byť "Answer"
                                item.answer() != null && item.answer().text() != null ? item.answer().text() : "")))
                .filter(item -> !item.answer().text().isBlank())
                .toList();
    }

    private List<String> sanitizeFeatures(List<String> f) {
        return f == null ? List.of() : f;
    }

    private List<String> sanitizeList(List<String> list) {
        return list == null ? List.of() : list.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    private void markAsProcessing(int id) {
        jdbcTemplate.update("UPDATE catalog.products_optimized SET status = 'processing' WHERE id = ?", id);
    }

    private void markAsFailed(int id, String e) {
        jdbcTemplate.update(
                "UPDATE catalog.products_optimized SET status = 'failed', validation_warnings = ? WHERE id = ?", e, id);
    }

    private String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void appendSectionIfContentExists(StringBuilder sb, String c, String t, String c1, String c2) {
        if ((c1 != null && !c1.isBlank()) || (c2 != null && !c2.isBlank())) {
            sb.append("<section class='").append(c).append(" mb-3'><h2>").append(t).append("</h2>");
            if (c1 != null && !c1.isBlank())
                sb.append("<p>").append(c1).append("</p>");
            if (c2 != null && !c2.isBlank())
                sb.append("<p>").append(c2).append("</p>");
            sb.append("</section>");
        }
    }

    // FIXED: JOIN so staging.humed_raw pre získanie GTIN, Balenie a Paleta
    private List<ProductToOptimize> loadPendingProducts() {
        return jdbcTemplate.query("""
                SELECT
                    po.id, po.sku, po.original_name, po.original_description,
                    COALESCE(hr.gtin, '') as gtin,
                    (hr.attributes->>'Balenie')::int as packaging_qty,
                    (hr.attributes->>'Paleta')::int as pallet_qty,
                    '' as category
                FROM catalog.products_optimized po
                LEFT JOIN staging.humed_raw hr ON hr.sku = po.sku
                WHERE po.status = 'pending'
                LIMIT 50
                """,
                (rs, rowNum) -> new ProductToOptimize(
                        rs.getInt("id"),
                        rs.getString("sku"),
                        rs.getString("original_name"),
                        rs.getString("original_description"),
                        rs.getString("category"),
                        rs.getString("gtin"),
                        rs.getObject("packaging_qty") != null ? rs.getInt("packaging_qty") : null,
                        rs.getObject("pallet_qty") != null ? rs.getInt("pallet_qty") : null));
    }

    public record ProductToOptimize(int id, String sku, String name, String description, String category, String gtin,
            Integer packagingQty, Integer palletQty) {
    }

    public record ScoreResult(int score, List<String> missingFields) {
    }
}