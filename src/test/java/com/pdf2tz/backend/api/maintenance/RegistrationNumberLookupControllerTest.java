package com.pdf2tz.backend.api.maintenance;

import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.RegistrationNumberLookupService;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupStatus;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationNumberLookupControllerTest {
    @Test
    void parsesPdfAndReturnsLookupWithoutAnLlmModelParameter() {
        PdfUploadReader uploadReader = mock(PdfUploadReader.class);
        PdfChunkingPipeline chunkingPipeline = mock(PdfChunkingPipeline.class);
        RegistrationNumberLookupService lookupService = mock(RegistrationNumberLookupService.class);
        when(uploadReader.read(any())).thenReturn(new byte[]{1});
        when(chunkingPipeline.chunk(any())).thenReturn(new ChunkedDocument(List.of()));
        when(lookupService.lookup(anyList())).thenReturn(new RegistrationNumberLookupResult(
                RegistrationNumberLookupStatus.NOT_FOUND, null, "FT10", null, null, null, null, Instant.now()
        ));

        var response = new RegistrationNumberLookupController(uploadReader, chunkingPipeline, lookupService)
                .findRegistrationNumber(new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[]{1}));

        assertEquals(RegistrationNumberLookupStatus.NOT_FOUND, response.getBody().status());
    }
}
