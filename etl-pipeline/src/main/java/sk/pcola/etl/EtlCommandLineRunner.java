package sk.pcola.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import sk.pcola.etl.catalog.CategoryMatcher;
import sk.pcola.etl.staging.humed.HumedStagingService;
import sk.pcola.etl.staging.humed.HumedSyncJob;

import java.util.Arrays;

/**
 * CLI runner pre manuálne spustenie ETL jobov.
 * 
 * Použitie:
 *   java -jar etl-pipeline.jar --sync-humed
 *   java -jar etl-pipeline.jar --stats
 * 
 * Bez argumentov aplikácia beží ako daemon so schedulermi.
 */
@Component
public class EtlCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EtlCommandLineRunner.class);

    private final HumedSyncJob humedSyncJob;
    private final CategoryMatcher categoryMatcher;

    public EtlCommandLineRunner(HumedSyncJob humedSyncJob, CategoryMatcher categoryMatcher) {
        this.humedSyncJob = humedSyncJob;
        this.categoryMatcher = categoryMatcher;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            log.info("ETL Pipeline started. Running in daemon mode with schedulers.");
            return;
        }

        log.info("CLI arguments: {}", Arrays.toString(args));

        for (String arg : args) {
            switch (arg) {
                case "--sync-humed" -> runHumedSync();
                case "--stats" -> printStats();
                case "--help" -> printHelp();
                default -> log.warn("Unknown argument: {}", arg);
            }
        }
    }

    private void runHumedSync() {
        log.info("Running HUMED sync manually...");
        try {
            HumedStagingService.UpsertResult result = humedSyncJob.sync();
            log.info("HUMED sync completed:");
            log.info("  Inserted: {}", result.inserted());
            log.info("  Updated:  {}", result.updated());
            log.info("  Unchanged: {}", result.unchanged());
            log.info("  Failed:   {}", result.failed());
            log.info("  Total:    {}", result.total());
        } catch (Exception e) {
            log.error("HUMED sync failed: {}", e.getMessage(), e);
        }
    }

    private void printStats() {
        log.info("=== ETL Statistics ===");

        CategoryMatcher.MappingStats humedStats = categoryMatcher.getStats("humed");
        log.info("HUMED Mapping:");
        log.info("  Exact match:   {}", humedStats.exact());
        log.info("  Pattern match: {}", humedStats.pattern());
        log.info("  Title match:   {}", humedStats.title());
        log.info("  Unmapped:      {}", humedStats.unmapped());
        log.info("  Excluded:      {}", humedStats.excluded());
        log.info("  Total:         {}", humedStats.total());
        log.info("  Match rate:    {:.1f}%", humedStats.matchedPercent());
    }

    private void printHelp() {
        System.out.println("""
            ETL Pipeline - CLI Commands
            
            Usage: java -jar etl-pipeline.jar [options]
            
            Options:
              --sync-humed    Run HUMED feed sync manually
              --stats         Print mapping statistics
              --help          Show this help
            
            Without arguments, the application runs as a daemon with scheduled jobs.
            """);
    }
}
