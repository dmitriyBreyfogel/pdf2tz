package com.pdf2tz.backend.api.maintenance.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** Невизуализированный результат LLM: DOCX будет строиться в следующем релизе. */
public record MaintenancePlanResponseDto(String modelCode, JsonNode plan) {
}
