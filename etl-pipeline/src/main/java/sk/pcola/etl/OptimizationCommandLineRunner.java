package sk.pcola.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import sk.pcola.etl.service.ProductOptimizationService;

import java.util.Arrays;
import java.util.List;

@Component
public class OptimizationCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OptimizationCommandLineRunner.class);
    private final ProductOptimizationService optimizationService;

    public OptimizationCommandLineRunner(ProductOptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @Override
    public void run(String... args) {
        List<String> argList = Arrays.asList(args);

        if (argList.contains("--optimize")) {
            runSyncOptimization();
        } else {
            log.info("Neznámy parameter alebo žiadny parameter. Použitie:");
            log.info("  --optimize          Spustí synchronnú optimalizáciu produktov");
        }
    }

    private void runSyncOptimization() {
        log.info("=== Spúšťam synchronnú optimalizáciu produktov ===");
        try {
            optimizationService.runOptimizationPipeline();
            log.info("=== Optimalizácia dokončená ===");
        } catch (Exception e) {
            log.error("Chyba počas optimalizácie: {}", e.getMessage(), e);
        }
    }
}
