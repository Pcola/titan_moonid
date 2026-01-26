package sk.pcola.etl.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Service pre mapovanie zdrojových kategórií na cieľové kategórie.
 * Používa pravidlá z catalog.category_rules tabuľky.
 * 
 * Postup mapovania:
 * 1. Skontroluj či kategória nie je v exclusions
 * 2. Presná zhoda na source_category_exact
 * 3. Pattern matching na source_category_pattern
 * 4. Pattern matching na title_pattern
 * 5. Ak nič nezaberie -> unmapped
 */
@Service
public class CategoryMatcher {

    private static final Logger log = LoggerFactory.getLogger(CategoryMatcher.class);

    private final JdbcTemplate jdbc;

    public CategoryMatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Výsledok mapovania.
     */
    public record MatchResult(
            Integer targetCategoryId,
            Integer matchedRuleId,
            String matchType  // "exact", "pattern", "title", "unmapped", "excluded"
    ) {
        public boolean isMatched() {
            return targetCategoryId != null && !"unmapped".equals(matchType) && !"excluded".equals(matchType);
        }

        public boolean isExcluded() {
            return "excluded".equals(matchType);
        }
    }

    /**
     * Nájde cieľovú kategóriu pre produkt.
     *
     * @param source názov zdroja (napr. "humed")
     * @param sourceCategoryName názov kategórie zo zdroja (napr. "Hygienický papier > Toaletný papier")
     * @param productTitle názov produktu (pre fallback pattern matching)
     * @return výsledok mapovania
     */
    public MatchResult match(String source, String sourceCategoryName, String productTitle) {

        // 1. Skontroluj exclusions
        if (isExcluded(source, sourceCategoryName)) {
            return new MatchResult(null, null, "excluded");
        }

        // 2. Presná zhoda
        Optional<MatchResult> result = matchExact(source, sourceCategoryName);
        if (result.isPresent()) {
            return result.get();
        }

        // 3. Pattern na kategóriu
        result = matchCategoryPattern(source, sourceCategoryName);
        if (result.isPresent()) {
            return result.get();
        }

        // 4. Pattern na title
        result = matchTitlePattern(source, productTitle);
        if (result.isPresent()) {
            return result.get();
        }

        // 5. Unmapped
        return new MatchResult(null, null, "unmapped");
    }

    /**
     * Skontroluje či kategória nie je v exclusions.
     */
    private boolean isExcluded(String source, String sourceCategoryName) {
        if (sourceCategoryName == null) {
            return false;
        }

        String sql = """
            SELECT COUNT(*) FROM catalog.category_exclusions
            WHERE source = ?
              AND is_active = true
              AND ? LIKE source_category_pattern
            """;

        Integer count = jdbc.queryForObject(sql, Integer.class, source, sourceCategoryName);
        return count != null && count > 0;
    }

    /**
     * Presná zhoda na source_category_exact.
     */
    private Optional<MatchResult> matchExact(String source, String sourceCategoryName) {
        if (sourceCategoryName == null) {
            return Optional.empty();
        }

        String sql = """
            SELECT id, target_category_id
            FROM catalog.category_rules
            WHERE source = ?
              AND source_category_exact = ?
              AND is_active = true
            ORDER BY priority
            LIMIT 1
            """;

        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                return Optional.of(new MatchResult(
                        rs.getInt("target_category_id"),
                        rs.getInt("id"),
                        "exact"
                ));
            }
            return Optional.empty();
        }, source, sourceCategoryName);
    }

    /**
     * Pattern matching na source_category_pattern.
     */
    private Optional<MatchResult> matchCategoryPattern(String source, String sourceCategoryName) {
        if (sourceCategoryName == null) {
            return Optional.empty();
        }

        String sql = """
            SELECT id, target_category_id
            FROM catalog.category_rules
            WHERE source = ?
              AND source_category_pattern IS NOT NULL
              AND ? LIKE source_category_pattern
              AND is_active = true
            ORDER BY priority
            LIMIT 1
            """;

        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                return Optional.of(new MatchResult(
                        rs.getInt("target_category_id"),
                        rs.getInt("id"),
                        "pattern"
                ));
            }
            return Optional.empty();
        }, source, sourceCategoryName);
    }

    /**
     * Pattern matching na title_pattern.
     */
    private Optional<MatchResult> matchTitlePattern(String source, String productTitle) {
        if (productTitle == null) {
            return Optional.empty();
        }

        String sql = """
            SELECT id, target_category_id
            FROM catalog.category_rules
            WHERE source = ?
              AND title_pattern IS NOT NULL
              AND ? ILIKE title_pattern
              AND is_active = true
            ORDER BY priority
            LIMIT 1
            """;

        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                return Optional.of(new MatchResult(
                        rs.getInt("target_category_id"),
                        rs.getInt("id"),
                        "title"
                ));
            }
            return Optional.empty();
        }, source, productTitle);
    }

    /**
     * Zaloguje výsledok mapovania do category_mapping_log.
     */
    public void logMapping(String source, String sourceProductId, String sourceSku,
                           String sourceCategoryRaw, MatchResult result) {
        String sql = """
            INSERT INTO catalog.category_mapping_log 
            (source, source_product_id, source_sku, source_category_raw, 
             matched_rule_id, target_category_id, match_type, mapped_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbc.update(sql,
                source,
                sourceProductId,
                sourceSku,
                sourceCategoryRaw,
                result.matchedRuleId(),
                result.targetCategoryId(),
                result.matchType(),
                Timestamp.from(Instant.now())
        );
    }

    /**
     * Získa štatistiky mapovania.
     */
    public MappingStats getStats(String source) {
        String sql = """
            SELECT 
                match_type,
                COUNT(*) as count
            FROM catalog.category_mapping_log
            WHERE source = ?
            GROUP BY match_type
            """;

        int exact = 0, pattern = 0, title = 0, unmapped = 0, excluded = 0;

        var results = jdbc.queryForList(sql, source);
        for (var row : results) {
            String type = (String) row.get("match_type");
            int count = ((Number) row.get("count")).intValue();
            switch (type) {
                case "exact" -> exact = count;
                case "pattern" -> pattern = count;
                case "title" -> title = count;
                case "unmapped" -> unmapped = count;
                case "excluded" -> excluded = count;
            }
        }

        return new MappingStats(exact, pattern, title, unmapped, excluded);
    }

    public record MappingStats(int exact, int pattern, int title, int unmapped, int excluded) {
        public int total() {
            return exact + pattern + title + unmapped + excluded;
        }

        public int matched() {
            return exact + pattern + title;
        }

        public double matchedPercent() {
            int tot = total();
            return tot > 0 ? (matched() * 100.0 / tot) : 0;
        }
    }
}
