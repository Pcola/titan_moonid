package sk.pcola.etl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO pre dáta obohatené z oficiálnej stránky výrobcu.
 * Používa sa pri Gemini grounding pre značkové produkty.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnrichedProductData(
        // EAN-13 alebo EAN-8 z oficiálnej stránky
        @JsonProperty("ean_gtin") String eanGtin,

        // SKU výrobcu (môže sa líšiť od SKU dodávateľa)
        @JsonProperty("manufacturer_sku") String manufacturerSku,

        // URL produktovej stránky výrobcu
        @JsonProperty("product_url") String productUrl,

        // Technické parametre z oficiálnej stránky
        @JsonProperty("specs") Map<String, String> specs,

        // Certifikácie (HACCP, ISO, EN normy)
        @JsonProperty("certifications") List<String> certifications,

        // Rozšírený popis z oficiálnej stránky
        @JsonProperty("detailed_description") String detailedDescription,

        // Skóre spoľahlivosti dát (0.0 - 1.0)
        @JsonProperty("confidence_score") Double confidenceScore,

        // Zdroje použité pri grounding (pre citácie)
        @JsonProperty("source_urls") List<String> sourceUrls) {
    /**
     * Vráti true ak boli nájdené validné dáta s vysokou spoľahlivosťou.
     */
    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore >= 0.7;
    }

    /**
     * Vráti true ak bol nájdený validný EAN kód.
     */
    public boolean hasValidEan() {
        if (eanGtin == null || eanGtin.isBlank())
            return false;
        String cleaned = eanGtin.replaceAll("[^0-9]", "");
        return cleaned.length() == 8 || cleaned.length() == 12 || cleaned.length() == 13;
    }

    /**
     * Vráti počet nájdených technických parametrov.
     */
    public int getSpecsCount() {
        return specs != null ? specs.size() : 0;
    }

    /**
     * Prázdny enrichment result pre produkty bez manufacturer match.
     */
    public static EnrichedProductData empty() {
        return new EnrichedProductData(null, null, null, Map.of(), List.of(), null, 0.0, List.of());
    }
}
