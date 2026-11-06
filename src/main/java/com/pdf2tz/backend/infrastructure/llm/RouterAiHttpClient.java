package com.pdf2tz.backend.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.application.maintenance.RouterAiClient;
import com.pdf2tz.backend.application.maintenance.MaintenancePlanDiagnosticsProperties;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class RouterAiHttpClient implements RouterAiClient {
    private final RouterAiProperties properties;
    private final MaintenancePlanDiagnosticsProperties diagnosticsProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    public RouterAiHttpClient(RouterAiProperties properties, MaintenancePlanDiagnosticsProperties diagnosticsProperties) {
        this.properties = properties;
        this.diagnosticsProperties = diagnosticsProperties;
    }
    @Override public JsonNode generate(MaintenanceLlmModel model, String instruction, String context) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) throw AppException.build(ErrorCode.LLM_NOT_CONFIGURED, "Не задана переменная окружения ROUTERAI_API_KEY");
        ObjectNode body = mapper.createObjectNode(); body.put("model", model.routerAiId()); body.put("temperature", 0);
        body.put("max_tokens", model.maxCompletionTokens());
        if (model.reasoningEffort() != null) {
            body.put("reasoning_effort", model.reasoningEffort());
        }
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
            if (content.isBlank()) {
                Map<String, Object> details = diagnosticsProperties.includeInvalidResponse()
                        ? Map.of("modelCode", model.routerAiId(), "routerResponse", rawResponse)
                        : Map.of();
                throw AppException.build(ErrorCode.LLM_INVALID_RESPONSE,
                        "Модель не вернула содержимое плана ТЗ", details);
            }
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
                    "sections":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,"required":["name","rows"],"properties":{"name":{"type":"string","minLength":1},"rows":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,"required":["number","deviceName","maintenanceType","maintenanceWorkDescription","registrationNumber","inventoryNumber","frequency","sourcePages"],"properties":{"number":{"type":"integer","minimum":1},"deviceName":{"type":"string","minLength":1},"maintenanceType":{"type":"string","minLength":1},"maintenanceWorkDescription":{"type":"array","minItems":1,"items":{"type":"string","minLength":1}},"registrationNumber":{"type":["string","null"]},"inventoryNumber":{"type":"null"},"frequency":{"type":"string","minLength":1},"sourcePages":{"type":"array","minItems":1,"items":{"type":"integer","minimum":1}}}}}}}},
                    "consumables":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["name","purpose","relatedRowNumbers","consumptionRate","sourcePages"],"properties":{"name":{"type":"string","minLength":1},"purpose":{"type":"string","minLength":1},"relatedRowNumbers":{"type":"array","minItems":1,"items":{"type":"integer","minimum":1}},"consumptionRate":{"type":["string","null"]},"sourcePages":{"type":"array","minItems":1,"items":{"type":"integer","minimum":1}}}}}}}
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Maintenance plan JSON schema is invalid", ex);
        }
    }
}
