package sk.pcola.etl.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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

    public EtlScheduler(HumedSyncJob humedSyncJob) {
        this.humedSyncJob = humedSyncJob;
    }

    /**
     * HUMED sync - denne o 02:00 (konfigurovateľné).
     */
    @Scheduled(cron = "${etl.scheduler.humed-cron}")
    public void syncHumed() {
        log.info("Scheduled HUMED sync starting...");
        try {
            HumedStagingService.UpsertResult result = humedSyncJob.sync();
            log.info("Scheduled HUMED sync completed: {}", result);
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
}
