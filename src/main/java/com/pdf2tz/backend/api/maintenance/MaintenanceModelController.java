package com.pdf2tz.backend.api.maintenance;

import com.pdf2tz.backend.api.maintenance.dto.MaintenanceLlmModelResponseDto;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class MaintenanceModelController implements MaintenanceModelApi {

    @Override
    public ResponseEntity<List<MaintenanceLlmModelResponseDto>> listModels() {
        List<MaintenanceLlmModelResponseDto> response = Arrays.stream(MaintenanceLlmModel.values())
                .map(model -> new MaintenanceLlmModelResponseDto(
                        model.routerAiId(), model.displayName(), model.provider(), model.schemaEnforced()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }
}
