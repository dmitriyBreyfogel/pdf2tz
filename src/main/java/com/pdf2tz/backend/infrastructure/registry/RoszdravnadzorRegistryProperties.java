package com.pdf2tz.backend.infrastructure.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "pdf2tz.registration-lookup")
public class RoszdravnadzorRegistryProperties {
    private boolean enabled = true;
    private String baseUrl = "https://elk.roszdravnadzor.gov.ru";
    private Duration cacheTtl = Duration.ofDays(30);
    private Duration minRequestInterval = Duration.ofSeconds(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
    public Duration getMinRequestInterval() { return minRequestInterval; }
    public void setMinRequestInterval(Duration minRequestInterval) { this.minRequestInterval = minRequestInterval; }
}
