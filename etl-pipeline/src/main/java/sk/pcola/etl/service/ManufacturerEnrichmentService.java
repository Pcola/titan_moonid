package sk.pcola.etl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sk.pcola.etl.dto.EnrichedProductData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Služba pre obohacovanie produktov dátami z oficiálnych stránok výrobcov.
 * Využíva Gemini API s Google Search grounding pre vyhľadávanie EAN kódov,
 * technických parametrov a certifikácií.
 * 
 * Podporované značky: TORK, KATRIN, KIMBERLY-CLARK, VIONE, BUZIL, LEWI, UNGER,
 * 3M, VILEDA, KÄRCHER
 */
@Service
public class ManufacturerEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ManufacturerEnrichmentService.class);

    // Gemini API s grounding - používame gemini-2.0-flash (podporuje google_search
    // tool)
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    // Mapa značka -> vyhľadávací pattern pre Google
    private static final Map<String, ManufacturerInfo> MANUFACTURERS = Map.ofEntries(
            // Papierová hygiena
            Map.entry("TORK", new ManufacturerInfo("TORK", "site:tork.sk OR site:torkglobal.com", "Essity")),
            Map.entry("KATRIN", new ManufacturerInfo("KATRIN", "site:katrin.com", "Metsä Tissue")),
            Map.entry("KIMBERLY",
                    new ManufacturerInfo("KIMBERLY-CLARK", "site:kcprofessional.com OR site:kimberly-clark.com",
                            "Kimberly-Clark")),
            Map.entry("SCOTT", new ManufacturerInfo("SCOTT", "site:kcprofessional.com", "Kimberly-Clark")),
            Map.entry("KLEENEX", new ManufacturerInfo("KLEENEX", "site:kcprofessional.com", "Kimberly-Clark")),
            Map.entry("WYPALL", new ManufacturerInfo("WYPALL", "site:kcprofessional.com", "Kimberly-Clark")),

            // Chémia a hygiena
            Map.entry("VIONE", new ManufacturerInfo("VIONE", "site:vione.sk OR site:bandm.sk", "B&M Slovakia")),
            Map.entry("BUZIL", new ManufacturerInfo("BUZIL", "site:buzil.com", "Buzil")),
            Map.entry("ECOLAB", new ManufacturerInfo("ECOLAB", "site:ecolab.com", "Ecolab")),
            Map.entry("DIVERSEY", new ManufacturerInfo("DIVERSEY", "site:diversey.com", "Diversey")),

            // Upratovacie pomôcky
            Map.entry("LEWI", new ManufacturerInfo("LEWI", "site:lewi.de", "LEWI")),
            Map.entry("UNGER", new ManufacturerInfo("UNGER", "site:ungerglobal.com", "Unger")),
            Map.entry("VILEDA", new ManufacturerInfo("VILEDA", "site:vileda-professional.com", "Vileda Professional")),
            Map.entry("KARCHER", new ManufacturerInfo("KÄRCHER", "site:kaercher.com", "Kärcher")),
            Map.entry("KÄRCHER", new ManufacturerInfo("KÄRCHER", "site:kaercher.com", "Kärcher")),

            // Ochranné pomôcky
            Map.entry("3M", new ManufacturerInfo("3M", "site:3m.com", "3M")),
            Map.entry("ANSELL", new ManufacturerInfo("ANSELL", "site:ansell.com", "Ansell")),
            Map.entry("NITRYLEX", new ManufacturerInfo("NITRYLEX", "site:mercatormedical.eu", "Mercator Medical")),

            // Gastro obaly
            Map.entry("PAPSTAR", new ManufacturerInfo("PAPSTAR", "site:papstar.de", "Papstar")),
            Map.entry("DUNI", new ManufacturerInfo("DUNI", "site:duni.com", "Duni")));

    // Pattern pre extrakciu EAN z textu (13 alebo 8 číslic)
    private static final Pattern EAN_PATTERN = Pattern.compile("\\b(\\d{13}|\\d{8})\\b");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.enrichment.enabled:true}")
    private boolean enrichmentEnabled;

    @Value("${gemini.enrichment.rate.limit.ms:1500}")
    private int rateLimitMs;

    public ManufacturerEnrichmentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Detekuje značku z názvu produktu.
     * 
     * @return Optional s názvom značky ak bola nájdená
     */
    public Optional<String> detectBrand(String productName) {
        if (productName == null || productName.isBlank()) {
            return Optional.empty();
        }

        String upperName = productName.toUpperCase();

        // Hľadaj značku na začiatku názvu (najčastejší pattern)
        String firstWord = upperName.split("\\s+")[0].replaceAll("[^A-Z0-9]", "");
        if (MANUFACTURERS.containsKey(firstWord)) {
            return Optional.of(firstWord);
        }

        // Hľadaj značku kdekoľvek v názve
        for (String brand : MANUFACTURERS.keySet()) {
            if (upperName.contains(brand)) {
                return Optional.of(brand);
            }
        }

        return Optional.empty();
    }

    /**
     * Obohatí produkt dátami z oficiálnej stránky výrobcu.
     * Používa Gemini API s Google Search grounding.
     * 
     * @param sku         SKU produktu z dodávateľského feedu
     * @param brand       Detekovaná značka
     * @param productName Názov produktu
     * @return EnrichedProductData s nájdenými informáciami
     */
    public EnrichedProductData enrichFromManufacturer(String sku, String brand, String productName) {
        if (!enrichmentEnabled) {
            log.debug("Manufacturer enrichment je vypnutý");
            return EnrichedProductData.empty();
        }

        ManufacturerInfo manufacturer = MANUFACTURERS.get(brand.toUpperCase());
        if (manufacturer == null) {
            log.warn("Neznáma značka pre enrichment: {}", brand);
            return EnrichedProductData.empty();
        }

        try {
            // Rate limiting
            Thread.sleep(rateLimitMs);

            String prompt = buildEnrichmentPrompt(sku, manufacturer, productName);
            String response = callGeminiWithGrounding(prompt);

            return parseEnrichmentResponse(response, sku);

        } catch (Exception e) {
            log.error("Chyba pri enrichment pre SKU {}: {}", sku, e.getMessage());
            return EnrichedProductData.empty();
        }
    }

    /**
     * Vytvorí prompt pre Gemini s grounding.
     */
    private String buildEnrichmentPrompt(String sku, ManufacturerInfo manufacturer, String productName) {
        return String.format("""
                Si B2B produktový analytik. Vyhľadaj oficiálne informácie o tomto produkte na stránke výrobcu.

                === PRODUKT ===
                SKU/Kód: %s
                Názov: %s
                Značka: %s
                Výrobca: %s

                === VYHĽADÁVANIE ===
                Použi Google Search s filtrom: %s
                Hľadaj produktovú stránku s týmto SKU alebo názvom.

                === EXTRAHUJ TIETO ÚDAJE ===
                1. EAN/GTIN kód - PRESNE 13 číslic (EAN-13) alebo 8 číslic (EAN-8)
                   KRITICKÉ: Ak EAN nenájdeš priamo na stránke, vráť null. NEVYMÝŠĽAJ!

                2. Oficiálny SKU výrobcu (môže sa líšiť od dodávateľského)

                3. Technické parametre:
                   - Rozmery (cm, mm)
                   - Objem (ml, l)
                   - Hmotnosť (g, kg)
                   - Materiál
                   - Farba
                   - Počet vrstiev (pre papier)
                   - Systém (T1, H2, S4...)

                4. Certifikácie a normy:
                   - HACCP
                   - ISO normy (9001, 14001, 22716)
                   - EN normy (455, 374, 388, 1276, 14476)
                   - Ecolabel, FSC, PEFC
                   - Biocídne povolenia

                5. Rozšírený popis produktu (min 100 slov) - z oficiálnej stránky, nie vymyslený

                === PRAVIDLÁ ===
                - Vráť LEN údaje ktoré si našiel na oficiálnej stránke výrobcu
                - Confidence score: 0.9+ ak údaje sú priamo z produktovej stránky, 0.5-0.8 ak z katalógu
                - Ak údaj nenájdeš, vráť null alebo prázdny zoznam

                === VÝSTUP (JSON) ===
                {
                  "ean_gtin": "8594123456789",
                  "manufacturer_sku": "oficiálny SKU",
                  "product_url": "https://...",
                  "specs": {
                    "objem": "1000 ml",
                    "systém": "S4",
                    "materiál": "plast ABS"
                  },
                  "certifications": ["HACCP", "EN 1276"],
                  "detailed_description": "Oficiálny popis produktu z webu výrobcu...",
                  "confidence_score": 0.85
                }
                """,
                sku, productName, manufacturer.brand(), manufacturer.company(), manufacturer.siteFilter());
    }

    /**
     * Volá Gemini API s Google Search grounding.
     */
    private String callGeminiWithGrounding(String prompt) throws Exception {
        // Request s google_search tool pre grounding
        Map<String, Object> request = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))),
                "tools", List.of(
                        Map.of("google_search", Map.of()) // Aktivácia grounding
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2, // Nízka teplota pre faktické dáta
                        "maxOutputTokens", 2000));

        String requestBody = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();

        int maxRetries = 3;
        int attempt = 0;

        while (true) {
            attempt++;
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }

            if (response.statusCode() == 429 && attempt < maxRetries) {
                log.warn("Rate limit, čakám 5s (pokus {}/{})", attempt, maxRetries);
                Thread.sleep(5000);
                continue;
            }

            throw new RuntimeException("Gemini API error " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Parsuje odpoveď z Gemini a extrahuje EnrichedProductData.
     */
    private EnrichedProductData parseEnrichmentResponse(String response, String sku) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode candidates = root.path("candidates");

        if (candidates.isEmpty()) {
            log.warn("Žiadni candidates v odpovedi pre SKU {}", sku);
            return EnrichedProductData.empty();
        }

        // Extrahuj text z odpovede
        String textContent = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        // Nájdi JSON v odpovedi
        String jsonContent = extractJsonFromText(textContent);
        if (jsonContent == null) {
            log.warn("Nenájdený JSON v odpovedi pre SKU {}", sku);
            return EnrichedProductData.empty();
        }

        // Parsuj JSON do DTO
        EnrichedProductData data = objectMapper.readValue(jsonContent, EnrichedProductData.class);

        // Extrahuj source URLs z grounding metadata ak existujú
        List<String> sourceUrls = extractGroundingUrls(root);

        // Vytvor nový objekt s doplnenými source URLs
        return new EnrichedProductData(
                data.eanGtin(),
                data.manufacturerSku(),
                data.productUrl(),
                data.specs(),
                data.certifications(),
                data.detailedDescription(),
                data.confidenceScore(),
                sourceUrls.isEmpty() ? data.sourceUrls() : sourceUrls);
    }

    /**
     * Extrahuje JSON z textovej odpovede.
     */
    private String extractJsonFromText(String text) {
        if (text == null)
            return null;

        // Odstráň markdown code block ak existuje
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        // Nájdi JSON objekt
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            return null;
        }

        return cleaned.substring(start, end + 1);
    }

    /**
     * Extrahuje URLs zo grounding metadata.
     */
    private List<String> extractGroundingUrls(JsonNode root) {
        List<String> urls = new ArrayList<>();

        try {
            JsonNode groundingMetadata = root.path("candidates")
                    .get(0)
                    .path("groundingMetadata");

            if (!groundingMetadata.isMissingNode()) {
                JsonNode groundingChunks = groundingMetadata.path("groundingChunks");
                if (groundingChunks.isArray()) {
                    for (JsonNode chunk : groundingChunks) {
                        String uri = chunk.path("web").path("uri").asText();
                        if (!uri.isBlank()) {
                            urls.add(uri);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Nepodarilo sa extrahovať grounding URLs: {}", e.getMessage());
        }

        return urls;
    }

    /**
     * Vráti zoznam podporovaných značiek.
     */
    public Set<String> getSupportedBrands() {
        return MANUFACTURERS.keySet();
    }

    /**
     * Info o výrobcovi.
     */
    private record ManufacturerInfo(String brand, String siteFilter, String company) {
    }
}
