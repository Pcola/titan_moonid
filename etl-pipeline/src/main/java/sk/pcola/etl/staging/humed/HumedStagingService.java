package sk.pcola.etl.staging.humed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Service pre ukladanie HUMED produktov do staging.humed_raw tabuľky.
 */
@Service
public class HumedStagingService {

    private static final Logger log = LoggerFactory.getLogger(HumedStagingService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public HumedStagingService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Výsledok upsert operácie.
     */
    public record UpsertResult(int inserted, int updated, int unchanged, int failed) {
        public int total() {
            return inserted + updated + unchanged + failed;
        }
    }

    /**
     * Uloží alebo aktualizuje produkt v staging tabuľke.
     * Používa checksum pre detekciu zmien.
     *
     * @param product produkt na uloženie
     * @return true ak bol produkt vložený/aktualizovaný, false ak bez zmeny
     */
    @Transactional
    public boolean upsert(HumedRawProduct product) {
        String checksum = computeChecksum(product);

        // Skontroluj či existuje a či sa zmenil
        String existingChecksum = jdbc.query(
                "SELECT checksum FROM staging.humed_raw WHERE feed_id = ?",
                rs -> rs.next() ? rs.getString("checksum") : null,
                product.getFeedId()
        );

        if (existingChecksum != null) {
            // Existuje - skontroluj checksum
            if (checksum.equals(existingChecksum)) {
                // Bez zmeny
                return false;
            }
            // Update
            return update(product, checksum);
        } else {
            // Insert
            return insert(product, checksum);
        }
    }

    private boolean insert(HumedRawProduct product, String checksum) {
        String sql = """
            INSERT INTO staging.humed_raw (
                feed_id, sku, gtin, title, description, link,
                price_purchase, price_retail, weight_grams,
                availability, condition,
                categories, images, attributes,
                checksum, imported_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
            """;

        try {
            Timestamp now = Timestamp.from(Instant.now());

            jdbc.update(sql,
                    product.getFeedId(),
                    product.getSku(),
                    product.getGtin(),
                    product.getTitle(),
                    product.getDescription(),
                    product.getLink(),
                    product.getPricePurchase(),
                    product.getPriceRetail(),
                    product.getWeightGrams(),
                    product.getAvailability(),
                    product.getCondition(),
                    toJson(product.getCategories()),
                    toJson(product.getImages()),
                    toJson(product.getAttributes()),
                    checksum,
                    now,
                    now
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to insert product {}: {}", product.getFeedId(), e.getMessage());
            return false;
        }
    }

    private boolean update(HumedRawProduct product, String checksum) {
        String sql = """
            UPDATE staging.humed_raw SET
                sku = ?, gtin = ?, title = ?, description = ?, link = ?,
                price_purchase = ?, price_retail = ?, weight_grams = ?,
                availability = ?, condition = ?,
                categories = ?::jsonb, images = ?::jsonb, attributes = ?::jsonb,
                checksum = ?, updated_at = ?
            WHERE feed_id = ?
            """;

        try {
            int rows = jdbc.update(sql,
                    product.getSku(),
                    product.getGtin(),
                    product.getTitle(),
                    product.getDescription(),
                    product.getLink(),
                    product.getPricePurchase(),
                    product.getPriceRetail(),
                    product.getWeightGrams(),
                    product.getAvailability(),
                    product.getCondition(),
                    toJson(product.getCategories()),
                    toJson(product.getImages()),
                    toJson(product.getAttributes()),
                    checksum,
                    Timestamp.from(Instant.now()),
                    product.getFeedId()
            );
            return rows > 0;
        } catch (Exception e) {
            log.error("Failed to update product {}: {}", product.getFeedId(), e.getMessage());
            return false;
        }
    }

    /**
     * Batch upsert pre viacero produktov.
     */
    @Transactional
    public UpsertResult upsertBatch(List<HumedRawProduct> products) {
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;

        for (HumedRawProduct product : products) {
            try {
                String checksum = computeChecksum(product);

                String existingChecksum = jdbc.query(
                        "SELECT checksum FROM staging.humed_raw WHERE feed_id = ?",
                        rs -> rs.next() ? rs.getString("checksum") : null,
                        product.getFeedId()
                );

                if (existingChecksum == null) {
                    if (insert(product, checksum)) {
                        inserted++;
                    } else {
                        failed++;
                    }
                } else if (!checksum.equals(existingChecksum)) {
                    if (update(product, checksum)) {
                        updated++;
                    } else {
                        failed++;
                    }
                } else {
                    unchanged++;
                }
            } catch (Exception e) {
                log.error("Failed to process product {}: {}", product.getFeedId(), e.getMessage());
                failed++;
            }
        }

        return new UpsertResult(inserted, updated, unchanged, failed);
    }

    /**
     * Vytvorí sync log záznam.
     */
    public long createSyncLog() {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO staging.humed_sync_log (started_at, status) VALUES (NOW(), 'running')",
                    new String[]{"id"}
            );
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    /**
     * Aktualizuje sync log po dokončení.
     */
    public void completeSyncLog(long syncLogId, UpsertResult result, String feedChecksum, String error) {
        String status = error == null ? "success" : "failed";

        jdbc.update("""
            UPDATE staging.humed_sync_log SET
                finished_at = NOW(),
                status = ?,
                products_total = ?,
                products_new = ?,
                products_updated = ?,
                products_unchanged = ?,
                error_message = ?,
                feed_checksum = ?
            WHERE id = ?
            """,
                status,
                result != null ? result.total() : 0,
                result != null ? result.inserted() : 0,
                result != null ? result.updated() : 0,
                result != null ? result.unchanged() : 0,
                error,
                feedChecksum,
                syncLogId
        );
    }

    /**
     * Vypočíta SHA-256 checksum pre detekciu zmien.
     */
    private String computeChecksum(HumedRawProduct product) {
        try {
            String data = String.join("|",
                    nullSafe(product.getSku()),
                    nullSafe(product.getTitle()),
                    nullSafe(product.getDescription()),
                    product.getPricePurchase() != null ? product.getPricePurchase().toPlainString() : "",
                    product.getPriceRetail() != null ? product.getPriceRetail().toPlainString() : "",
                    product.getWeightGrams() != null ? product.getWeightGrams().toString() : "",
                    nullSafe(product.getAvailability()),
                    toJson(product.getCategories()),
                    toJson(product.getImages()),
                    toJson(product.getAttributes())
            );

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
