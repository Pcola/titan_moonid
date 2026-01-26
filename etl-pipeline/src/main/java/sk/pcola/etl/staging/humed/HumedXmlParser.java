package sk.pcola.etl.staging.humed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.pcola.etl.common.util.EncodingUtil;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * StAX parser pre HUMED XML feed.
 * Memory efficient - spracováva produkt po produkte.
 * 
 * Feed štruktúra (Google Shopping RSS):
 * <rss>
 *   <channel>
 *     <item>
 *       <g:id>922</g:id>
 *       <g:title>...</g:title>
 *       ...
 *     </item>
 *   </channel>
 * </rss>
 */
@Component
public class HumedXmlParser {

    private static final Logger log = LoggerFactory.getLogger(HumedXmlParser.class);

    private static final String NS_GOOGLE = "http://base.google.com/ns/1.0";
    private static final String ITEM = "item";

    /**
     * Parsuje HUMED feed zo súboru a volá consumer pre každý produkt.
     *
     * @param feedPath cesta k XML súboru
     * @param consumer callback pre každý sparsovaný produkt
     * @return počet spracovaných produktov
     */
    public int parse(Path feedPath, Consumer<HumedRawProduct> consumer) {
        log.info("Parsing HUMED feed from: {}", feedPath);

        try (InputStream is = Files.newInputStream(feedPath)) {
            return parse(is, consumer);
        } catch (Exception e) {
            log.error("Failed to parse HUMED feed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse HUMED feed", e);
        }
    }

    /**
     * Parsuje HUMED feed z InputStream.
     */
    public int parse(InputStream inputStream, Consumer<HumedRawProduct> consumer) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Security: disable external entities
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        int count = 0;

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT && ITEM.equals(reader.getLocalName())) {
                    HumedRawProduct product = parseItem(reader);
                    if (product != null && product.getFeedId() != null) {
                        consumer.accept(product);
                        count++;

                        if (count % 500 == 0) {
                            log.debug("Parsed {} products", count);
                        }
                    }
                }
            }

            reader.close();

        } catch (XMLStreamException e) {
            log.error("XML parsing error: {}", e.getMessage(), e);
            throw new RuntimeException("XML parsing error", e);
        }

        log.info("Finished parsing HUMED feed. Total products: {}", count);
        return count;
    }

    /**
     * Parsuje jeden <item> element.
     */
    private HumedRawProduct parseItem(XMLStreamReader reader) throws XMLStreamException {
        HumedRawProduct product = new HumedRawProduct();
        String currentElement = null;
        StringBuilder textContent = new StringBuilder();

        // Pre additional_fields
        String additionalFieldName = null;
        String additionalFieldValue = null;
        boolean inAdditionalField = false;

        // Pre categories
        String categoryId = null;
        String categoryName = null;
        boolean inCategory = false;

        while (reader.hasNext()) {
            int event = reader.next();

            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    currentElement = reader.getLocalName();
                    textContent.setLength(0);

                    if ("category".equals(currentElement)) {
                        inCategory = true;
                        categoryId = null;
                        categoryName = null;
                    } else if ("additional_field".equals(currentElement)) {
                        inAdditionalField = true;
                        additionalFieldName = null;
                        additionalFieldValue = null;
                    }
                }

                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    textContent.append(reader.getText());
                }

                case XMLStreamConstants.END_ELEMENT -> {
                    String elementName = reader.getLocalName();
                    String text = textContent.toString().trim();

                    // Koniec item - vrátiť produkt
                    if (ITEM.equals(elementName)) {
                        return product;
                    }

                    // Spracovanie vnorených elementov
                    if (inCategory) {
                        switch (elementName) {
                            case "category_id" -> categoryId = text;
                            case "category_name" -> categoryName = EncodingUtil.normalize(text);
                            case "category" -> {
                                if (categoryId != null || categoryName != null) {
                                    product.addCategory(new HumedRawProduct.HumedCategory(categoryId, categoryName));
                                }
                                inCategory = false;
                            }
                        }
                    } else if (inAdditionalField) {
                        switch (elementName) {
                            // Feed má <n> namiesto <name>
                            case "n", "name" -> additionalFieldName = EncodingUtil.normalize(text);
                            case "value" -> additionalFieldValue = text;
                            case "additional_field" -> {
                                product.addAttribute(additionalFieldName, additionalFieldValue);
                                inAdditionalField = false;
                            }
                        }
                    } else {
                        // Hlavné elementy produktu
                        switch (elementName) {
                            case "id" -> product.setFeedId(text);
                            case "sku" -> product.setSku(text);
                            case "gtin" -> product.setGtin(text);
                            case "title" -> product.setTitle(EncodingUtil.normalize(text));
                            case "description" -> product.setDescription(EncodingUtil.normalize(text));
                            case "link" -> product.setLink(text);
                            case "cenaVhumede" -> product.setPricePurchase(parsePrice(text));
                            case "price" -> product.setPriceRetail(parsePrice(text));
                            case "weight" -> product.setWeightGrams(parseWeight(text));
                            case "availability" -> product.setAvailability(text);
                            case "condition" -> product.setCondition(text);
                            case "image_link" -> product.addImage(text);
                            case "additional_image_link" -> product.addImage(text);
                        }
                    }

                    textContent.setLength(0);
                }
            }
        }

        return product;
    }

    /**
     * Parsuje cenu - odstráni "EUR" a whitespace.
     * Príklad: "11.931 EUR" -> 11.931
     */
    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String cleaned = text.replace("EUR", "").trim();
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse price: {}", text);
            return null;
        }
    }

    /**
     * Parsuje hmotnosť - odstráni "g" a konvertuje na gramy.
     * Príklad: "450.00g" -> 450
     */
    private Integer parseWeight(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String cleaned = text.toLowerCase().replace("g", "").trim();
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            log.warn("Cannot parse weight: {}", text);
            return null;
        }
    }

    /**
     * Parsuje všetky produkty do zoznamu.
     * Pozor: načíta všetko do pamäte - pre veľké feedy použiť parse() s consumer.
     */
    public List<HumedRawProduct> parseAll(Path feedPath) {
        List<HumedRawProduct> products = new ArrayList<>();
        parse(feedPath, products::add);
        return products;
    }
}
