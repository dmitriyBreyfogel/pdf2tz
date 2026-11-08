package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/** Applies a verified registry number uniformly to all rows of one-device plan. */
@Component
public class RegistrationNumberPlanEnricher {

    public JsonNode apply(JsonNode plan, RegistrationNumberLookupResult lookup) {
        if (lookup.status() != RegistrationNumberLookupStatus.FOUND && lookup.status() != RegistrationNumberLookupStatus.EXPIRED) {
            return plan;
        }
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode row : section.path("rows")) {
                ((ObjectNode) row).put("registrationNumber", lookup.registrationNumber());
                if (lookup.status() == RegistrationNumberLookupStatus.EXPIRED) {
                    String notice = lookup.expirationDate() == null ? "Просрочено"
                            : "Просрочено с " + lookup.expirationDate().format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
                    ((ObjectNode) row).put("registrationNumberExpiryNotice", notice);
                }
            }
        }
        return plan;
    }
}
