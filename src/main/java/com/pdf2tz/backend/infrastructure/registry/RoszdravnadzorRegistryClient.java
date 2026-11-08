package com.pdf2tz.backend.infrastructure.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.application.maintenance.RegistrationRegistryClient;
import com.pdf2tz.backend.application.maintenance.model.RegistrationRegistryRecord;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the public filter endpoint used by the Roszdravnadzor registry widget.
 *
 * <p>The endpoint is not presented by the authority as a supported integration API,
 * so a failure is deliberately handled by the application as an optional enrichment
 * failure rather than as a failed maintenance-plan request.</p>
 */
@Component
public class RoszdravnadzorRegistryClient implements RegistrationRegistryClient {
    private static final String FILTER_PATH = "/public-gateway/registered-med-product/api/v1/med-product/filter-public?page=0&size=10";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final RoszdravnadzorRegistryProperties properties;

    public RoszdravnadzorRegistryClient(RoszdravnadzorRegistryProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RegistrationRegistryRecord> search(String query) {
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("textSearch", query, "statusIds", List.of()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + FILTER_PATH))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Registry returned HTTP " + response.statusCode());
            }
            return records(mapper.readTree(response.body()));
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось выполнить поиск в реестре Росздравнадзора", exception);
        }
    }

    private List<RegistrationRegistryRecord> records(JsonNode response) {
        List<RegistrationRegistryRecord> result = new ArrayList<>();
        for (JsonNode record : response.path("content")) {
            String number = text(record, "noRu");
            String name = text(record, "name");
            if (number == null || name == null) {
                continue;
            }
            result.add(new RegistrationRegistryRecord(
                    record.path("id").asLong(), number, name,
                    text(record.path("producer"), "name"),
                    "active".equalsIgnoreCase(text(record.path("status"), "code"))
            ));
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

}
