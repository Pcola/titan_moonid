package sk.pcola.etl.staging.humed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HumedXmlParserTest {

    private final HumedXmlParser parser = new HumedXmlParser();

    @Test
    void shouldParseBasicProduct() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
            <channel>
            <item>
              <g:id>922</g:id>
              <g:title>Test Product</g:title>
              <description><![CDATA[Test description]]></description>
              <g:sku>SKU123</g:sku>
              <g:cenaVhumede>6.467</g:cenaVhumede>
              <g:price>11.931 EUR</g:price>
              <g:weight>450.00g</g:weight>
              <g:availability>in stock</g:availability>
              <g:image_link>https://example.com/image.jpg</g:image_link>
              <categories>
                <category>
                  <category_name>Test Category</category_name>
                  <category_id>137</category_id>
                </category>
              </categories>
              <g:additional_fields>
                <g:additional_field>
                  <n>Balenie</n>
                  <value>24</value>
                </g:additional_field>
              </g:additional_fields>
            </item>
            </channel>
            </rss>
            """;

        List<HumedRawProduct> products = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), products::add);

        assertEquals(1, products.size());

        HumedRawProduct product = products.getFirst();
        assertEquals("922", product.getFeedId());
        assertEquals("Test Product", product.getTitle());
        assertEquals("SKU123", product.getSku());
        assertEquals("6.467", product.getPricePurchase().toPlainString());
        assertEquals("11.931", product.getPriceRetail().toPlainString());
        assertEquals(450, product.getWeightGrams());
        assertEquals("in stock", product.getAvailability());
        assertEquals(1, product.getImages().size());
        assertEquals(1, product.getCategories().size());
        assertEquals("137", product.getCategories().getFirst().getId());
        assertEquals("24", product.getAttributes().get("Balenie"));
    }

    @Test
    void shouldHandleMultipleCategories() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
            <channel>
            <item>
              <g:id>1</g:id>
              <g:title>Product</g:title>
              <g:sku>SKU1</g:sku>
              <categories>
                <category>
                  <category_name>Main Category</category_name>
                  <category_id>100</category_id>
                </category>
                <category>
                  <category_name>Main Category &gt; Sub Category</category_name>
                  <category_id>101</category_id>
                </category>
              </categories>
            </item>
            </channel>
            </rss>
            """;

        List<HumedRawProduct> products = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), products::add);

        assertEquals(1, products.size());
        assertEquals(2, products.getFirst().getCategories().size());

        // Deepest category should be the last one
        HumedRawProduct.HumedCategory deepest = products.getFirst().getDeepestCategory();
        assertEquals("101", deepest.getId());
        assertTrue(deepest.getName().contains("Sub Category"));
    }

    @Test
    void shouldParseFromFile(@TempDir Path tempDir) throws Exception {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
            <channel>
            <item>
              <g:id>1</g:id>
              <g:title>Product 1</g:title>
              <g:sku>SKU1</g:sku>
            </item>
            <item>
              <g:id>2</g:id>
              <g:title>Product 2</g:title>
              <g:sku>SKU2</g:sku>
            </item>
            </channel>
            </rss>
            """;

        Path feedFile = tempDir.resolve("test_feed.xml");
        Files.writeString(feedFile, xml);

        List<HumedRawProduct> products = parser.parseAll(feedFile);

        assertEquals(2, products.size());
        assertEquals("1", products.get(0).getFeedId());
        assertEquals("2", products.get(1).getFeedId());
    }

    @Test
    void shouldHandleEmptyFeed() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
            <channel>
            </channel>
            </rss>
            """;

        List<HumedRawProduct> products = new ArrayList<>();
        int count = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), products::add);

        assertEquals(0, count);
        assertTrue(products.isEmpty());
    }

    @Test
    void shouldParseMultipleImages() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
            <channel>
            <item>
              <g:id>1</g:id>
              <g:title>Product</g:title>
              <g:sku>SKU1</g:sku>
              <g:image_link>https://example.com/image1.jpg</g:image_link>
              <g:additional_image_link>https://example.com/image2.jpg</g:additional_image_link>
            </item>
            </channel>
            </rss>
            """;

        List<HumedRawProduct> products = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), products::add);

        assertEquals(2, products.getFirst().getImages().size());
    }
}
