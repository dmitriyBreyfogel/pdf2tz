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
import org.springframework.web.client.RestClientException;

@Component
public class RouterAiHttpClient implements RouterAiClient {
    private final RouterAiProperties properties; private final ObjectMapper mapper;
    public RouterAiHttpClient(RouterAiProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }
    @Override public JsonNode generate(MaintenanceLlmModel model, String instruction, String context) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) throw AppException.build(ErrorCode.LLM_NOT_CONFIGURED, "Не задана переменная окружения ROUTERAI_API_KEY");
        ObjectNode body = mapper.createObjectNode(); body.put("model", model.routerAiId()); body.put("temperature", 0); body.put("max_tokens", 8000);
        var messages = body.putArray("messages"); messages.addObject().put("role", "system").put("content", instruction); messages.addObject().put("role", "user").put("content", context);
        if (model.schemaEnforced()) body.putObject("response_format").put("type", "json_object");
        try {
            JsonNode response = RestClient.builder().baseUrl(properties.baseUrl()).defaultHeader("Authorization", "Bearer " + properties.apiKey()).build()
                    .post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            String content = response.path("choices").path(0).path("message").path("content").asText();
            return mapper.readTree(content);
        } catch (AppException ex) { throw ex; } catch (Exception ex) { throw AppException.build(ErrorCode.LLM_REQUEST_FAILED, "Не удалось получить ответ RouterAI"); }
    }
}
