package com.pdf2tz.backend.api.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.MaintenanceDocumentService;
import com.pdf2tz.backend.application.maintenance.MaintenancePlanService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceDocumentControllerTest {
    @Test
    void returnsDocxPlanAndConsumablesInOneMultipartResponse() throws Exception {
        PdfUploadReader uploadReader = mock(PdfUploadReader.class);
        MaintenancePlanService planService = mock(MaintenancePlanService.class);
        MaintenanceDocumentService documentService = mock(MaintenanceDocumentService.class);
        when(uploadReader.read(any())).thenReturn(new byte[]{1});
        when(planService.generate(any(), any())).thenReturn(new ObjectMapper().readTree("""
                {"sections":[],"consumables":[{"name":"Салфетка"}]}
                """));
        when(documentService.create(any())).thenReturn(new byte[]{1, 2, 3});

        var response = new MaintenanceDocumentController(uploadReader, planService, documentService)
                .generateDocument(new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[]{1}), "model");

        assertTrue(response.getHeaders().getContentType().toString().startsWith("multipart/mixed"));
        String body = new String(response.getBody(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("name=\"document\""));
        assertTrue(body.contains("name=\"plan\""));
        assertTrue(body.contains("name=\"consumables\""));
        assertTrue(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("Салфетка"));
        assertArrayEquals(new byte[]{1, 2, 3}, new byte[]{response.getBody()[body.indexOf("\r\n\r\n") + 4], response.getBody()[body.indexOf("\r\n\r\n") + 5], response.getBody()[body.indexOf("\r\n\r\n") + 6]});
    }
}
