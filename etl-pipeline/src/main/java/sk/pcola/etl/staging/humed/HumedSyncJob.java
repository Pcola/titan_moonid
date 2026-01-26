package sk.pcola.etl.staging.humed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sk.pcola.etl.config.HumedConfig;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Orchestrácia HUMED sync procesu:
 * 1. Stiahnutie feedu (HTTP alebo lokálny súbor)
 * 2. Parsovanie XML
 * 3. Upsert do staging tabuľky
 * 4. Logovanie výsledku
 */
@Service
public class HumedSyncJob {

    private static final Logger log = LoggerFactory.getLogger(HumedSyncJob.class);

    private final HumedConfig config;
    private final HumedXmlParser parser;
    private final HumedStagingService stagingService;

    public HumedSyncJob(HumedConfig config, HumedXmlParser parser, HumedStagingService stagingService) {
        this.config = config;
        this.parser = parser;
        this.stagingService = stagingService;
    }

    /**
     * Spustí HUMED sync.
     */
    public HumedStagingService.UpsertResult sync() {
        if (!config.isEnabled()) {
            log.info("HUMED sync is disabled");
            return new HumedStagingService.UpsertResult(0, 0, 0, 0);
        }

        log.info("Starting HUMED sync job");
        long syncLogId = stagingService.createSyncLog();

        try {
            // 1. Získaj feed
            Path feedPath = getFeed();
            String feedChecksum = computeFileChecksum(feedPath);

            // 2. Parsuj a ukladaj v dávkach
            HumedStagingService.UpsertResult result = parseAndStore(feedPath);

            // 3. Zaloguj úspech
            stagingService.completeSyncLog(syncLogId, result, feedChecksum, null);

            log.info("HUMED sync completed. Inserted: {}, Updated: {}, Unchanged: {}, Failed: {}",
                    result.inserted(), result.updated(), result.unchanged(), result.failed());

            return result;

        } catch (Exception e) {
            log.error("HUMED sync failed: {}", e.getMessage(), e);
            stagingService.completeSyncLog(syncLogId, null, null, e.getMessage());
            throw new RuntimeException("HUMED sync failed", e);
        }
    }

    /**
     * Získa feed - stiahne z URL alebo použije lokálny súbor.
     */
    private Path getFeed() throws Exception {
        String feedUrl = config.getFeedUrl();
        String feedPath = config.getFeedPath();

        // Ak existuje lokálny súbor, použij ho
        if (feedPath != null && !feedPath.isBlank()) {
            Path localPath = Path.of(feedPath);
            if (Files.exists(localPath)) {
                log.info("Using local feed file: {}", localPath);
                return localPath;
            }
        }

        // Inak stiahni z URL
        if (feedUrl != null && !feedUrl.isBlank()) {
            return downloadFeed(feedUrl, feedPath);
        }

        throw new IllegalStateException("No feed source configured (URL or path)");
    }

    /**
     * Stiahne feed z URL.
     */
    private Path downloadFeed(String url, String targetPath) throws Exception {
        log.info("Downloading HUMED feed from: {}", url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        Path target = targetPath != null ? Path.of(targetPath) : Files.createTempFile("humed_feed_", ".xml");

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download feed. HTTP status: " + response.statusCode());
        }

        try (InputStream is = response.body()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Feed downloaded to: {} ({} bytes)", target, Files.size(target));
        return target;
    }

    /**
     * Parsuje feed a ukladá produkty v dávkach.
     */
    private HumedStagingService.UpsertResult parseAndStore(Path feedPath) {
        final int BATCH_SIZE = 100;
        List<HumedRawProduct> batch = new ArrayList<>(BATCH_SIZE);

        int totalInserted = 0;
        int totalUpdated = 0;
        int totalUnchanged = 0;
        int totalFailed = 0;

        // Wrapper pre akumuláciu výsledkov v lambda
        int[] totals = {0, 0, 0, 0}; // inserted, updated, unchanged, failed

        parser.parse(feedPath, product -> {
            batch.add(product);

            if (batch.size() >= BATCH_SIZE) {
                HumedStagingService.UpsertResult result = stagingService.upsertBatch(batch);
                totals[0] += result.inserted();
                totals[1] += result.updated();
                totals[2] += result.unchanged();
                totals[3] += result.failed();
                batch.clear();
            }
        });

        // Spracuj zvyšok
        if (!batch.isEmpty()) {
            HumedStagingService.UpsertResult result = stagingService.upsertBatch(batch);
            totals[0] += result.inserted();
            totals[1] += result.updated();
            totals[2] += result.unchanged();
            totals[3] += result.failed();
        }

        return new HumedStagingService.UpsertResult(totals[0], totals[1], totals[2], totals[3]);
    }

    /**
     * Vypočíta checksum súboru pre detekciu zmien feedu.
     */
    private String computeFileChecksum(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Failed to compute file checksum: {}", e.getMessage());
            return null;
        }
    }
}
