package sk.pcola.etl.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OptimizedProductDto(
        // Identita
        @JsonProperty("parentProductName") @JsonAlias("parent_product_name") String parentProductName,
        @JsonProperty("brandDetected") @JsonAlias("brand_detected") String brandDetected,

        // SEO
        @JsonProperty("suggestedFocusKeyword") @JsonAlias("suggested_focus_keyword") String suggestedFocusKeyword,
        @JsonProperty("searchIntent") @JsonAlias("search_intent") String searchIntent,

        // Textové bloky (Raw Text - nie HTML!)
        @JsonProperty("nameH1") @JsonAlias("name_h1") String nameH1,
        @JsonProperty("metaTitle") @JsonAlias("meta_title") String metaTitle,
        @JsonProperty("metaDescription") @JsonAlias("meta_description") String metaDescription,
        @JsonProperty("shortDescription") @JsonAlias("short_description") String shortDescription,

        @JsonProperty("sectionProblem") @JsonAlias("section_problem") String sectionProblem,
        @JsonProperty("sectionSolution") @JsonAlias("section_solution") String sectionSolution,
        @JsonProperty("sectionUsage") @JsonAlias("section_usage") String sectionUsage,
        @JsonProperty("sectionApplications") @JsonAlias("section_applications") String sectionApplications,
        @JsonProperty("sectionAdvantages") @JsonAlias("section_advantages") String sectionAdvantages,
        @JsonProperty("imageAltText") @JsonAlias("image_alt_text") String imageAltText,

        // Štruktúrované dáta
        @JsonProperty("specs") Map<String, String> specs,
        @JsonProperty("faq") List<FaqItem> faq,
        @JsonProperty("features") List<String> features,

        // B2B & AEO/GEO/LLM signály
        @JsonProperty("targetSegments") @JsonAlias("target_segments") List<String> targetSegments,
        @JsonProperty("certifications") List<String> certifications
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FaqItem(
            @JsonProperty("@type") String type,
            @JsonProperty("name") String question,
            @JsonProperty("acceptedAnswer") AcceptedAnswer answer
    ) {
        public record AcceptedAnswer(
                @JsonProperty("@type") String type,
                @JsonProperty("text") String text
        ) {}
    }
}
