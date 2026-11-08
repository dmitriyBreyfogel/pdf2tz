package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistrationNumberPlanEnricherTest {
    @Test
    void appliesVerifiedNumberToEveryMaintenanceRow() throws Exception {
        var plan = new ObjectMapper().readTree("""
                {"sections":[{"rows":[{"registrationNumber":null},{"registrationNumber":null}]}],"consumables":[]}
                """);
        var result = new RegistrationNumberPlanEnricher().apply(plan, new RegistrationNumberLookupResult(
                RegistrationNumberLookupStatus.FOUND, "РЗН 2017/5614", "FT10", null, null, null, Instant.now()
        ));

        assertEquals("РЗН 2017/5614", result.path("sections").path(0).path("rows").path(0).path("registrationNumber").asText());
        assertEquals("РЗН 2017/5614", result.path("sections").path(0).path("rows").path(1).path("registrationNumber").asText());
    }
}
