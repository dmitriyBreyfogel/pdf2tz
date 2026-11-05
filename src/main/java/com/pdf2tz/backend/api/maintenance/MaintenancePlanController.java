package com.pdf2tz.backend.api.maintenance;

import com.pdf2tz.backend.api.maintenance.dto.MaintenancePlanResponseDto;
import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.MaintenancePlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MaintenancePlanController implements MaintenancePlanApi {
    private final PdfUploadReader uploadReader;
    private final MaintenancePlanService service;
    public MaintenancePlanController(PdfUploadReader uploadReader, MaintenancePlanService service) { this.uploadReader = uploadReader; this.service = service; }
    @Override public ResponseEntity<MaintenancePlanResponseDto> generate(MultipartFile file, String modelCode) {
        return ResponseEntity.ok(new MaintenancePlanResponseDto(modelCode, service.generate(uploadReader.read(file), modelCode)));
    }
}
