package sk.pcola.etl.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sk.pcola.etl.catalog.ProductNormalizer;
import sk.pcola.etl.staging.humed.HumedStagingService;
import sk.pcola.etl.staging.humed.HumedSyncJob;

/**
 * Scheduler pre ETL joby.
 *
 * Konfigurácia cron výrazov v application.properties:
 * - etl.scheduler.humed-cron
 * - etl.scheduler.corwell-products-cron
 * - etl.scheduler.corwell-stock-cron
 */
@Component
public class EtlScheduler {

    private static final Logger log = LoggerFactory.getLogger(EtlScheduler.class);

    private final HumedSyncJob humedSyncJob;
    private final ProductNormalizer productNormalizer;

    public EtlScheduler(HumedSyncJob humedSyncJob, ProductNormalizer productNormalizer) {
        this.humedSyncJob = humedSyncJob;
        this.productNormalizer = productNormalizer;
    }

    /**
     * HUMED sync - denne o 02:00 (konfigurovateľné).
     * Po sync automaticky spustí normalizáciu.
     */
    @Scheduled(cron = "${etl.scheduler.humed-cron}")
    public void syncHumed() {
        log.info("Scheduled HUMED sync starting...");
        try {
            HumedStagingService.UpsertResult syncResult = humedSyncJob.sync();
            log.info("Scheduled HUMED sync completed: {}", syncResult);

            // Po úspešnom sync spusti normalizáciu
            if (syncResult.total() > 0) {
                log.info("Starting product normalization...");
                ProductNormalizer.NormalizeResult normalizeResult = productNormalizer.normalizeHumed();
                log.info("Normalization completed: {}", normalizeResult);
            }
        } catch (Exception e) {
            log.error("Scheduled HUMED sync failed: {}", e.getMessage(), e);
        }
    }

    // TODO: Corwell joby budú pridané po implementácii SOAP klienta

    /**
     * Manuálne spustenie HUMED sync (pre testovanie alebo CLI).
     */
    public HumedStagingService.UpsertResult runHumedSyncManually() {
        log.info("Manual HUMED sync starting...");
        return humedSyncJob.sync();
    }

    /**
     * Manuálne spustenie normalizácie.
     */
    public ProductNormalizer.NormalizeResult runNormalizeManually() {
        log.info("Manual normalization starting...");
        return productNormalizer.normalizeHumed();
    }
}
