package sk.pcola.etl.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncodingUtilTest {

    @Test
    void shouldFixMojibake() {
        // Simulácia mojibake: "Hygienický" zakódované ako UTF-8, dekódované ako ISO-8859-1
        String mojibake = "HygienickÃ½";
        String expected = "Hygienický";

        String result = EncodingUtil.fixMojibake(mojibake);

        assertEquals(expected, result);
    }

    @Test
    void shouldDecodeHtmlEntities() {
        String input = "Category &gt; Subcategory &amp; More";
        String expected = "Category > Subcategory & More";

        String result = EncodingUtil.decodeHtmlEntities(input);

        assertEquals(expected, result);
    }

    @Test
    void shouldNormalizeCombined() {
        // Reálny príklad z HUMED feedu
        String input = "HygienickÃ½ papier &gt; ToaletnÃ½ papier";
        String expected = "Hygienický papier > Toaletný papier";

        String result = EncodingUtil.normalize(input);

        assertEquals(expected, result);
    }

    @Test
    void shouldHandleNull() {
        assertNull(EncodingUtil.fixMojibake(null));
        assertNull(EncodingUtil.decodeHtmlEntities(null));
        assertNull(EncodingUtil.normalize(null));
    }

    @Test
    void shouldHandleEmptyString() {
        assertEquals("", EncodingUtil.fixMojibake(""));
        assertEquals("", EncodingUtil.decodeHtmlEntities(""));
        assertEquals("", EncodingUtil.normalize(""));
    }

    @Test
    void shouldPreserveCleanText() {
        String clean = "Čistý slovenský text";
        assertEquals(clean, EncodingUtil.normalize(clean));
    }
}
