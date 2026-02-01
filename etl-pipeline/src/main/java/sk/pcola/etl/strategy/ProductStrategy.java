package sk.pcola.etl.strategy;

import java.util.List;
import java.util.Map;

public enum ProductStrategy {

    // 1. PAPIEROVÁ HYGIENA
    PAPER_HYGIENE {
        @Override
        public String getPromptContext() {
            return "Kritické pre B2B papier: SYSTÉM (T1/H2...), Počet vrstiev, Návin (m), Počet útržkov, Materiál (100% Celulóza/Recyklát), Farba.";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("systém", 5, "počet_vrstiev", 4, "materiál", 4, "návin", 3, "počet_útržkov", 3, "balenie", 2);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 2. ZÁSOBNÍKY A DÁVKOVAČE
    DISPENSERS_AND_BINS {
        @Override
        public String getPromptContext() {
            return "Kritické pre zásobníky: KOMPATIBILNÝ SYSTÉM (S1/T2...), Materiál (ABS/Nerez), Rozmery (VxŠxH), Objem (l).";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("systém", 5, "materiál", 4, "rozmer", 4, "objem", 3, "farba", 2);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 3. CHÉMIA
    CHEMICALS {
        @Override
        public String getPromptContext() {
            return "Kritické pre chémiu: pH hodnota, Riedenue, EN Normy (dezinfekcia), HACCP, Typ povrchu, Vôňa.";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("ph", 5, "objem", 4, "určenie", 4, "norma", 3, "forma", 3);
        }
        @Override
        public String getSafetyWarning() {
            return "Profesionálny chemický prípravok. Pred použitím si preštudujte Kartu bezpečnostných údajov (KBÚ) a používajte ochranné pomôcky.";
        }
    },

    // 4. OSVIEŽOVAČE
    AIR_CARE {
        @Override
        public String getPromptContext() {
            return "Kritické pre vône: Typ vône, Trvácnosť (dni), Typ (Sprej/Elektrický), Systém (A1).";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("vôňa", 5, "trvácnosť", 4, "typ", 4, "systém", 3, "balenie", 2);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 5. OCHRANNÉ POMÔCKY
    PROTECTIVE_GEAR {
        @Override
        public String getPromptContext() {
            return "Kritické pre rukavice: Veľkosť (S/M/L/XL), Materiál (Nitril/Latex), Púdrovanie, AQL, Normy (EN 374).";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("veľkosť", 5, "materiál", 5, "norma", 4, "púdrovanie", 3, "balenie", 2);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 6. UPRATOVACIE POMÔCKY (Hardware)
    CLEANING_HARDWARE {
        @Override
        public String getPromptContext() {
            return "Kritické pre pomôcky: Typ uchytenia, Šírka záberu (cm), Materiál vlákna, Farebné kódovanie.";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("typ_uchytenia", 5, "rozmer", 5, "materiál", 4, "farba", 3, "kompatibilita", 3);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 7. ODPADOVÉ HOSPODÁRSTVO
    WASTE_MANAGEMENT {
        @Override
        public String getPromptContext() {
            // POZOR: µm pre vrecia
            return "Kritické pre vrecia: Objem (l), Hrúbka (v µm), Rozmer (cm), Materiál (LDPE/HDPE), Typ.";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("objem", 5, "hrúbka", 5, "rozmer", 4, "materiál", 4, "typ", 3);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 8. GASTRO OBALY
    GASTRO_DISPOSABLES {
        @Override
        public String getPromptContext() {
            return "Kritické pre gastro: Materiál (XPS/PP/BIO), Objem (ml), Rozmer (mm), Kompostovateľnosť.";
        }
        @Override
        public Map<String, Integer> getSpecWeights() {
            return Map.of("materiál", 5, "objem", 4, "rozmer", 4, "balenie", 3);
        }
        @Override
        public String getSafetyWarning() { return ""; }
    },

    // 9. FALLBACK
    GENERIC {
        @Override
        public String getPromptContext() { return "Zameraj sa na funkčné vlastnosti a parametre."; }
        @Override
        public Map<String, Integer> getSpecWeights() { return Map.of("balenie", 5, "rozmer", 3); }
        @Override
        public String getSafetyWarning() { return ""; }
    };

    public abstract String getPromptContext();
    public abstract Map<String, Integer> getSpecWeights();
    public abstract String getSafetyWarning();

    public List<String> getRequiredSpecs() {
        return List.copyOf(getSpecWeights().keySet());
    }
}