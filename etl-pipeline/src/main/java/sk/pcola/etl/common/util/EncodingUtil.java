package sk.pcola.etl.common.util;

import java.nio.charset.StandardCharsets;

/**
 * Utility pre opravu encoding problémov v HUMED feede.
 * Feed má mojibake - UTF-8 text interpretovaný ako ISO-8859-1.
 * Príklad: "HygienickÃ½ papier" -> "Hygienický papier"
 */
public final class EncodingUtil {

    private EncodingUtil() {
    }

    /**
     * Opraví mojibake encoding (UTF-8 interpretované ako ISO-8859-1).
     *
     * @param text text s mojibake
     * @return opravený UTF-8 text
     */
    public static String fixMojibake(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Nahradí HTML entity v texte.
     * Hlavne &gt; -> > a &amp; -> &
     *
     * @param text text s HTML entities
     * @return text s nahradenými entities
     */
    public static String decodeHtmlEntities(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'");
    }

    /**
     * Kombinuje fix mojibake a HTML entities.
     */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String fixed = fixMojibake(text);
        fixed = decodeHtmlEntities(fixed);
        return fixed.trim();
    }
}
