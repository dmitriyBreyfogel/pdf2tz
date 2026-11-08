package com.pdf2tz.backend.api.maintenance;

import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Local diagnostic endpoint; it parses a PDF and never invokes an LLM. */
@RequestMapping("/api/v1/maintenance")
public interface RegistrationNumberLookupApi {

    @PostMapping("/registration-number")
    ResponseEntity<RegistrationNumberLookupResult> findRegistrationNumber(
            @RequestParam("file") MultipartFile file
    );
}
