package com.pdf2tz.backend.application.maintenance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Explicitly opt-in diagnostics for local investigation of a provider response.
 * Never enable this for a public deployment: a response can contain text from an
 * uploaded manual and is returned verbatim only while diagnosing a contract issue.
 */
@Component
@ConfigurationProperties(prefix = "pdf2tz.llm.diagnostics")
public class MaintenancePlanDiagnosticsProperties {
    private boolean includeInvalidResponse;

    public boolean includeInvalidResponse() {
        return includeInvalidResponse;
    }

    public void setIncludeInvalidResponse(boolean includeInvalidResponse) {
        this.includeInvalidResponse = includeInvalidResponse;
    }
}
