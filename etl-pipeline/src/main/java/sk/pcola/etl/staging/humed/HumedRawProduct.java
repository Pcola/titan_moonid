package sk.pcola.etl.staging.humed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reprezentuje jeden produkt z HUMED XML feedu.
 * Mapuje štruktúru:
 * - g:id, g:sku, g:gtin
 * - g:title, description
 * - g:cenaVhumede (nákupná), g:price (predajná)
 * - g:weight, g:availability
 * - categories (list), images (list), additional_fields (map)
 */
public class HumedRawProduct {

    private String feedId;          // g:id
    private String sku;             // g:sku
    private String gtin;            // g:gtin
    private String title;           // g:title
    private String description;     // description CDATA
    private String link;            // g:link
    private BigDecimal pricePurchase;  // g:cenaVhumede
    private BigDecimal priceRetail;    // g:price (bez "EUR")
    private Integer weightGrams;       // g:weight (parsované)
    private String availability;       // g:availability
    private String condition;          // g:condition

    private List<HumedCategory> categories = new ArrayList<>();
    private List<String> images = new ArrayList<>();
    private Map<String, String> attributes = new HashMap<>();  // Balenie, Paleta

    // Getters and Setters

    public String getFeedId() {
        return feedId;
    }

    public void setFeedId(String feedId) {
        this.feedId = feedId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public BigDecimal getPricePurchase() {
        return pricePurchase;
    }

    public void setPricePurchase(BigDecimal pricePurchase) {
        this.pricePurchase = pricePurchase;
    }

    public BigDecimal getPriceRetail() {
        return priceRetail;
    }

    public void setPriceRetail(BigDecimal priceRetail) {
        this.priceRetail = priceRetail;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(Integer weightGrams) {
        this.weightGrams = weightGrams;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public List<HumedCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<HumedCategory> categories) {
        this.categories = categories;
    }

    public void addCategory(HumedCategory category) {
        this.categories.add(category);
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public void addImage(String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            this.images.add(imageUrl);
        }
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String name, String value) {
        if (name != null && value != null) {
            this.attributes.put(name, value);
        }
    }

    /**
     * Vráti najhlbšiu kategóriu (posledná v zozname).
     */
    public HumedCategory getDeepestCategory() {
        if (categories.isEmpty()) {
            return null;
        }
        return categories.getLast();
    }

    @Override
    public String toString() {
        return "HumedRawProduct{" +
                "feedId='" + feedId + '\'' +
                ", sku='" + sku + '\'' +
                ", title='" + title + '\'' +
                ", pricePurchase=" + pricePurchase +
                ", priceRetail=" + priceRetail +
                '}';
    }

    /**
     * Vnorená trieda pre kategóriu.
     */
    public static class HumedCategory {
        private String id;
        private String name;

        public HumedCategory() {
        }

        public HumedCategory(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "HumedCategory{id='" + id + "', name='" + name + "'}";
        }
    }
}
