package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupStatus;
import org.springframework.stereotype.Component;

/** Applies a verified registry number uniformly to all rows of one-device plan. */
@Component
public class RegistrationNumberPlanEnricher {

    public JsonNode apply(JsonNode plan, RegistrationNumberLookupResult lookup) {
        if (lookup.status() != RegistrationNumberLookupStatus.FOUND) {
            return plan;
        }
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode row : section.path("rows")) {
                ((ObjectNode) row).put("registrationNumber", lookup.registrationNumber());
            }
        }
        return plan;
    }
}
