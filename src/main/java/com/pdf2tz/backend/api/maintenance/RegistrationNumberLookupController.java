package com.pdf2tz.backend.api.maintenance;

import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.RegistrationNumberLookupService;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Exposes registration lookup separately so it can be tested without generating a plan. */
@RestController
public class RegistrationNumberLookupController implements RegistrationNumberLookupApi {
    private final PdfUploadReader uploadReader;
    private final PdfChunkingPipeline chunkingPipeline;
    private final RegistrationNumberLookupService lookupService;

    public RegistrationNumberLookupController(PdfUploadReader uploadReader,
                                              PdfChunkingPipeline chunkingPipeline,
                                              RegistrationNumberLookupService lookupService) {
        this.uploadReader = uploadReader;
        this.chunkingPipeline = chunkingPipeline;
        this.lookupService = lookupService;
    }

    @Override
    public ResponseEntity<RegistrationNumberLookupResult> findRegistrationNumber(MultipartFile file) {
        return ResponseEntity.ok(lookupService.lookup(chunkingPipeline.chunk(uploadReader.read(file)).chunks()));
    }
}
