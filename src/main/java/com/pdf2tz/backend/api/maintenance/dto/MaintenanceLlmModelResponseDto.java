package com.pdf2tz.backend.api.maintenance.dto;

/** Разрешённая модель для генерации ТЗ. */
public record MaintenanceLlmModelResponseDto(
        String modelCode,
        String displayName,
        String provider,
        boolean schemaEnforced
) {
}
