package sk.pcola.etl.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Normalizácia produktov zo staging.humed_raw do catalog.products.
 * 
 * Proces:
 * 1. Načítaj produkty zo staging (is_excluded = false)
 * 2. Aplikuj category mapping
 * 3. Vypočítaj maržu z cien (price_cost, price_b2b sú z feedu)
 * 4. Upsert do catalog.products + catalog.product_sources
 */
@Service
public class ProductNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ProductNormalizer.class);

    private static final String SOURCE_HUMED = "humed";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CategoryMatcher categoryMatcher;

    public ProductNormalizer(JdbcTemplate jdbc, ObjectMapper objectMapper, CategoryMatcher categoryMatcher) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.categoryMatcher = categoryMatcher;
    }

    /**
     * Výsledok normalizácie.
     */
    public record NormalizeResult(
            int processed,
            int created,
            int updated,
            int skippedExcluded,
            int skippedUnmapped,
            int failed
    ) {}

    /**
     * Normalizuje všetky HUMED produkty zo staging do catalog.
     */
    @Transactional
    public NormalizeResult normalizeHumed() {
        log.info("Starting HUMED product normalization");

        int processed = 0;
        int created = 0;
        int updated = 0;
        int skippedExcluded = 0;
        int skippedUnmapped = 0;
        int failed = 0;

        // Načítaj všetky produkty zo staging
        List<StagingProduct> stagingProducts = loadStagingProducts();
        log.info("Loaded {} products from staging", stagingProducts.size());

        for (StagingProduct staging : stagingProducts) {
            processed++;

            try {
                // Získaj najhlbšiu kategóriu
                String categoryPath = extractDeepestCategoryPath(staging.categoriesJson());

                // Aplikuj category matching
                CategoryMatcher.MatchResult matchResult = categoryMatcher.match(
                        SOURCE_HUMED,
                        categoryPath,
                        staging.title()
                );

                // Zaloguj mapovanie
                categoryMatcher.logMapping(
                        SOURCE_HUMED,
                        staging.feedId(),
                        staging.sku(),
                        categoryPath,
                        matchResult
                );

                // Preskočenie excluded
                if (matchResult.isExcluded()) {
                    markAsExcluded(staging.feedId(), "Category excluded");
                    skippedExcluded++;
                    continue;
                }

                // Preskočenie unmapped
                if (!matchResult.isMatched()) {
                    log.debug("Unmapped product: {} - {}", staging.sku(), categoryPath);
                    skippedUnmapped++;
                    continue;
                }

                // Upsert do catalog
                boolean isNew = upsertProduct(staging, matchResult.targetCategoryId());
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }

            } catch (Exception e) {
                log.error("Failed to normalize product {}: {}", staging.feedId(), e.getMessage());
                failed++;
            }

            if (processed % 500 == 0) {
                log.info("Processed {} products", processed);
            }
        }

        NormalizeResult result = new NormalizeResult(processed, created, updated, skippedExcluded, skippedUnmapped, failed);
        log.info("Normalization completed: {}", result);

        return result;
    }

    /**
     * Načíta produkty zo staging tabuľky.
     */
    private List<StagingProduct> loadStagingProducts() {
        String sql = """
            SELECT feed_id, sku, title, description,
                   price_purchase, price_retail, weight_grams,
                   categories, images, attributes,
                   availability
            FROM staging.humed_raw
            WHERE is_excluded = false
            ORDER BY feed_id
            """;

        return jdbc.query(sql, new StagingProductRowMapper());
    }

    /**
     * Extrahuje cestu najhlbšej kategórie z JSON.
     * Kategórie sú uložené ako: [{"id":"137","name":"Main"},{"id":"214","name":"Main > Sub"}]
     * Vráti názov poslednej (najhlbšej) kategórie.
     */
    private String extractDeepestCategoryPath(String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isBlank()) {
            return null;
        }

        try {
            List<Map<String, String>> categories = objectMapper.readValue(
                    categoriesJson,
                    new TypeReference<>() {}
            );

            if (categories.isEmpty()) {
                return null;
            }

            // Posledná kategória je najhlbšia
            Map<String, String> deepest = categories.getLast();
            return deepest.get("name");

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse categories JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Označí produkt v staging ako excluded.
     */
    private void markAsExcluded(String feedId, String reason) {
        jdbc.update(
                "UPDATE staging.humed_raw SET is_excluded = true, exclusion_reason = ? WHERE feed_id = ?",
                reason,
                feedId
        );
    }

    /**
     * Upsert produktu do catalog.products a catalog.product_sources.
     * 
     * @return true ak bol vytvorený nový produkt, false ak aktualizovaný
     */
    private boolean upsertProduct(StagingProduct staging, Integer categoryId) {
        // Skontroluj či existuje v product_sources
        Integer existingProductId = jdbc.query(
                "SELECT product_id FROM catalog.product_sources WHERE source = ? AND source_id = ?",
                rs -> rs.next() ? rs.getInt("product_id") : null,
                SOURCE_HUMED,
                staging.feedId()
        );

        // Vypočítaj maržu
        BigDecimal marginPercent = calculateMarginPercent(staging.pricePurchase(), staging.priceRetail());

        // Parse attributes pre pack_quantity
        Integer packQuantity = parsePackQuantity(staging.attributesJson());

        // Konvertuj váhu na kg
        BigDecimal weightKg = staging.weightGrams() != null
                ? new BigDecimal(staging.weightGrams()).divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP)
                : null;

        Timestamp now = Timestamp.from(Instant.now());

        if (existingProductId == null) {
            // INSERT nový produkt
            return insertNewProduct(staging, categoryId, marginPercent, packQuantity, weightKg, now);
        } else {
            // UPDATE existujúci produkt
            updateExistingProduct(existingProductId, staging, categoryId, marginPercent, packQuantity, weightKg, now);
            return false;
        }
    }

    private boolean insertNewProduct(StagingProduct staging, Integer categoryId,
                                     BigDecimal marginPercent, Integer packQuantity,
                                     BigDecimal weightKg, Timestamp now) {
        // Generuj SKU (použijeme HUMED SKU)
        String catalogSku = staging.sku();

        // Skontroluj unikátnosť SKU
        Integer existingBySku = jdbc.query(
                "SELECT id FROM catalog.products WHERE sku = ?",
                rs -> rs.next() ? rs.getInt("id") : null,
                catalogSku
        );

        if (existingBySku != null) {
            // SKU existuje - pridaj len nový source
            insertProductSource(existingBySku, staging, now);
            return false;
        }

        // Insert do catalog.products
        String insertProduct = """
            INSERT INTO catalog.products (
                sku, name, description, category_id,
                price_cost, price_b2b, margin_percent,
                weight_kg, pack_quantity,
                images, attributes,
                stock_status, is_active,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
            RETURNING id
            """;

        Integer productId = jdbc.queryForObject(
                insertProduct,
                Integer.class,
                catalogSku,
                staging.title(),
                staging.description(),
                categoryId,
                staging.pricePurchase(),
                staging.priceRetail(),
                marginPercent,
                weightKg,
                packQuantity,
                staging.imagesJson(),
                staging.attributesJson(),
                mapAvailability(staging.availability()),
                true,
                now,
                now
        );

        // Insert do product_sources
        insertProductSource(productId, staging, now);

        return true;
    }

    private void updateExistingProduct(Integer productId, StagingProduct staging, Integer categoryId,
                                       BigDecimal marginPercent, Integer packQuantity,
                                       BigDecimal weightKg, Timestamp now) {
        String updateProduct = """
            UPDATE catalog.products SET
                name = ?,
                description = ?,
                category_id = ?,
                price_cost = ?,
                price_b2b = ?,
                margin_percent = ?,
                weight_kg = ?,
                pack_quantity = ?,
                images = ?::jsonb,
                attributes = ?::jsonb,
                stock_status = ?,
                updated_at = ?
            WHERE id = ?
            """;

        jdbc.update(
                updateProduct,
                staging.title(),
                staging.description(),
                categoryId,
                staging.pricePurchase(),
                staging.priceRetail(),
                marginPercent,
                weightKg,
                packQuantity,
                staging.imagesJson(),
                staging.attributesJson(),
                mapAvailability(staging.availability()),
                now,
                productId
        );

        // Update product_sources
        jdbc.update("""
            UPDATE catalog.product_sources SET
                source_price_purchase = ?,
                source_price_retail = ?,
                last_seen_at = ?
            WHERE product_id = ? AND source = ?
            """,
                staging.pricePurchase(),
                staging.priceRetail(),
                now,
                productId,
                SOURCE_HUMED
        );
    }

    private void insertProductSource(Integer productId, StagingProduct staging, Timestamp now) {
        String sql = """
            INSERT INTO catalog.product_sources (
                product_id, source, source_id, source_sku,
                source_price_purchase, source_price_retail,
                is_primary, priority, last_seen_at, is_active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source, source_id) DO UPDATE SET
                source_price_purchase = EXCLUDED.source_price_purchase,
                source_price_retail = EXCLUDED.source_price_retail,
                last_seen_at = EXCLUDED.last_seen_at
            """;

        jdbc.update(sql,
                productId,
                SOURCE_HUMED,
                staging.feedId(),
                staging.sku(),
                staging.pricePurchase(),
                staging.priceRetail(),
                true,
                1,
                now,
                true
        );
    }

    /**
     * Vypočíta percentuálnu maržu.
     * Marža = (predajná - nákupná) / predajná * 100
     */
    private BigDecimal calculateMarginPercent(BigDecimal cost, BigDecimal sell) {
        if (cost == null || sell == null || sell.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return sell.subtract(cost)
                .divide(sell, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Parsuje pack quantity z attributes JSON.
     */
    private Integer parsePackQuantity(String attributesJson) {
        if (attributesJson == null || attributesJson.isBlank()) {
            return null;
        }
        try {
            Map<String, String> attrs = objectMapper.readValue(attributesJson, new TypeReference<>() {});
            String balenie = attrs.get("Balenie");
            if (balenie != null) {
                return Integer.parseInt(balenie);
            }
        } catch (Exception e) {
            log.debug("Failed to parse pack quantity: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Mapuje availability z feedu na stock_status.
     */
    private String mapAvailability(String feedAvailability) {
        if (feedAvailability == null) {
            return "instock";
        }
        return switch (feedAvailability.toLowerCase()) {
            case "in stock", "in_stock" -> "instock";
            case "out of stock", "out_of_stock" -> "outofstock";
            case "preorder", "pre-order" -> "onbackorder";
            default -> "instock";
        };
    }

    /**
     * Record pre staging produkt.
     */
    private record StagingProduct(
            String feedId,
            String sku,
            String title,
            String description,
            BigDecimal pricePurchase,
            BigDecimal priceRetail,
            Integer weightGrams,
            String categoriesJson,
            String imagesJson,
            String attributesJson,
            String availability
    ) {}

    /**
     * RowMapper pre StagingProduct.
     */
    private static class StagingProductRowMapper implements RowMapper<StagingProduct> {
        @Override
        public StagingProduct mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StagingProduct(
                    rs.getString("feed_id"),
                    rs.getString("sku"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getBigDecimal("price_purchase"),
                    rs.getBigDecimal("price_retail"),
                    rs.getObject("weight_grams", Integer.class),
                    rs.getString("categories"),
                    rs.getString("images"),
                    rs.getString("attributes"),
                    rs.getString("availability")
            );
        }
    }
}
