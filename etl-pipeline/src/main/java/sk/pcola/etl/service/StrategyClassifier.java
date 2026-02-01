package sk.pcola.etl.service;

import org.springframework.stereotype.Component;
import sk.pcola.etl.strategy.ProductStrategy;

@Component
public class StrategyClassifier {

    public ProductStrategy detect(String category, String name) {
        String input = (safe(category) + " " + safe(name)).toLowerCase();

        // 1. OCHRANNÉ POMÔCKY
        if (matches(input, "rukavic", "respirátor", "rúšk", "odev", "štít", "nitril", "latex", "vinyl", "pracovn", "jednorazov")) {
            return ProductStrategy.PROTECTIVE_GEAR;
        }

        // 2. ODPADOVÉ HOSPODÁRSTVO
        if (matches(input, "vrec", "odpad", "ldpe", "hdpe", "kôš", "popolník", "sáčk", "separač", "stojan", "kontajner")) {
            return ProductStrategy.WASTE_MANAGEMENT;
        }

        // 3. OSVIEŽOVAČE A WC BLOKY
        if (matches(input, "osviežovač", "vonn", "sitk", "pisoár", "blok wc", "wc blok", "pohlcovač", "aróma", "spray", "kazet")) {
            return ProductStrategy.AIR_CARE;
        }

        // 4. ZÁSOBNÍKY A DÁVKOVAČE
        if (matches(input, "zásobník", "dávkovač", "kôš", "koš", "stojan", "držiak toalet", "kúpeľňové sety")) {
            return ProductStrategy.DISPENSERS_AND_BINS;
        }

        // 5. PAPIEROVÁ HYGIENA
        if (matches(input, "papier", "utierk", "toalet", "vreckovk", "obrúsk", "servítk", "rolk", "autocut", "matic", "zz", "skladané", "perforáci", "podložk", "netkaná", "vlhčené")) {
            return ProductStrategy.PAPER_HYGIENE;
        }

        // 6. GASTRO OBALY
        if (matches(input, "menu box", "pohár", "viečk", "misk", "taniere", "príbor", "slamk", "krabic", "obal na jedlo", "kelímk")) {
            return ProductStrategy.GASTRO_DISPOSABLES;
        }

        // 7. CHÉMIA A MYDLÁ
        if (matches(input, "mydl", "tekuté", "tuhé", "speňovacie", "čisti", "čistič", "prostried", "dezinfek", "kúpeľň", "kuchyň", "podlah", "nábytok", "okná", "profi", "past", "jar", "pur", "savo", "bref", "pulirapid", "clin", "fixinela")) {
            return ProductStrategy.CHEMICALS;
        }

        // 8. UPRATOVACIE POMÔCKY (Pridané: duster, oprašovač, prachovka)
        if (matches(input, "mop", "vedr", "vozík", "metla", "kefa", "stierk", "držiak", "tyč", "násad", "pad", "handr", "mikrovlákn", "hubk", "špong", "duster", "oprašovač", "prachovk")) {
            return ProductStrategy.CLEANING_HARDWARE;
        }

        // Fallback
        return ProductStrategy.GENERIC;
    }

    private boolean matches(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}