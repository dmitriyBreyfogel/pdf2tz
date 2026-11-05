package com.pdf2tz.backend.api.maintenance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1/maintenance")
public interface MaintenancePlanApi {
    @PostMapping("/plan")
    ResponseEntity<String> generate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("modelCode") String modelCode
    );
}
