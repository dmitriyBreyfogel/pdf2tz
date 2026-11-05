package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;

/** Порт единственного внешнего LLM-шлюза. */
public interface RouterAiClient {
    JsonNode generate(MaintenanceLlmModel model, String systemInstruction, String context);
}
