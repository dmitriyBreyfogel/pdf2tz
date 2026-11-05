package com.pdf2tz.backend.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.application.maintenance.RouterAiClient;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RouterAiHttpClient implements RouterAiClient {
    private final RouterAiProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    public RouterAiHttpClient(RouterAiProperties properties) { this.properties = properties; }
    @Override public JsonNode generate(MaintenanceLlmModel model, String instruction, String context) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) throw AppException.build(ErrorCode.LLM_NOT_CONFIGURED, "Не задана переменная окружения ROUTERAI_API_KEY");
        ObjectNode body = mapper.createObjectNode(); body.put("model", model.routerAiId()); body.put("temperature", 0); body.put("max_tokens", 8000);
        var messages = body.putArray("messages"); messages.addObject().put("role", "system").put("content", instruction); messages.addObject().put("role", "user").put("content", context);
        if (model.schemaEnforced()) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode schema = format.putObject("json_schema");
            schema.put("name", "maintenance_plan");
            schema.put("strict", true);
            schema.set("schema", maintenancePlanSchema());
        }
        try {
            String rawResponse = RestClient.builder().baseUrl(properties.baseUrl()).defaultHeader("Authorization", "Bearer " + properties.apiKey()).build()
                    .post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body)).retrieve().body(String.class);
            JsonNode response = mapper.readTree(rawResponse);
            String content = response.path("choices").path(0).path("message").path("content").asText();
            return mapper.readTree(jsonContent(content));
        } catch (AppException ex) { throw ex; } catch (Exception ex) {
            throw AppException.build(ErrorCode.LLM_REQUEST_FAILED,
                    "Не удалось получить ответ RouterAI: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /** Some providers return JSON in a Markdown fence despite an explicit JSON instruction. */
    static String jsonContent(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) return trimmed;
        return trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
    }

    private JsonNode maintenancePlanSchema() {
        try {
            return mapper.readTree("""
                    {"type":"object","additionalProperties":false,"required":["sections","consumables"],"properties":{
                    "sections":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["name","rows"],"properties":{"name":{"type":"string"},"rows":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["number","deviceName","maintenanceType","maintenanceWorkDescription","registrationNumber","inventoryNumber","frequency","sourcePages"],"properties":{"number":{"type":"integer"},"deviceName":{"type":"string"},"maintenanceType":{"type":"string"},"maintenanceWorkDescription":{"type":"array","items":{"type":"string"}},"registrationNumber":{"type":["string","null"]},"inventoryNumber":{"type":"null"},"frequency":{"type":"string"},"sourcePages":{"type":"array","items":{"type":"integer"}}}}}}}},
                    "consumables":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["name","purpose","relatedRowNumbers","consumptionRate","sourcePages"],"properties":{"name":{"type":"string"},"purpose":{"type":"string"},"relatedRowNumbers":{"type":"array","items":{"type":"integer"}},"consumptionRate":{"type":["string","null"]},"sourcePages":{"type":"array","items":{"type":"integer"}}}}}}}
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Maintenance plan JSON schema is invalid", ex);
        }
    }
}
