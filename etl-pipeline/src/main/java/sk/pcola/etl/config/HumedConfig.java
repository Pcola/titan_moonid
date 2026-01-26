package sk.pcola.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "etl.humed")
public class HumedConfig {

    private String feedUrl;
    private String feedPath;
    private boolean enabled = true;

    public String getFeedUrl() {
        return feedUrl;
    }

    public void setFeedUrl(String feedUrl) {
        this.feedUrl = feedUrl;
    }

    public String getFeedPath() {
        return feedPath;
    }

    public void setFeedPath(String feedPath) {
        this.feedPath = feedPath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
