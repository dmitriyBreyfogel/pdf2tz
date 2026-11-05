package com.pdf2tz.backend.api.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.MaintenancePlanService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MaintenancePlanController implements MaintenancePlanApi {
    private final PdfUploadReader uploadReader;
    private final MaintenancePlanService service;
    private final ObjectMapper mapper = new ObjectMapper();
    public MaintenancePlanController(PdfUploadReader uploadReader, MaintenancePlanService service) {
        this.uploadReader = uploadReader;
        this.service = service;
    }
    @Override public ResponseEntity<String> generate(MultipartFile file, String modelCode) {
        ObjectNode response = mapper.createObjectNode();
        response.put("modelCode", modelCode);
        response.set("plan", service.generate(uploadReader.read(file), modelCode));
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response));
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось сериализовать ответ с планом ТЗ", exception);
        }
    }
}
