package com.pdf2tz.backend.api.maintenance;

import com.pdf2tz.backend.api.maintenance.dto.MaintenanceLlmModelResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/** Каталог моделей, которые разрешены в генерации ТЗ. */
@RequestMapping("/api/v1/maintenance")
public interface MaintenanceModelApi {

    @GetMapping("/models")
    ResponseEntity<List<MaintenanceLlmModelResponseDto>> listModels();
}
