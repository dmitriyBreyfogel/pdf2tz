package com.pdf2tz.backend.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pdf2tz.llm.router-ai")
public class RouterAiProperties {
    private String baseUrl = "https://routerai.ru/api/v1";
    private String apiKey = "";
    public String baseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String apiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
