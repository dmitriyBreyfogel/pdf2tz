package com.pdf2tz.backend.api.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.MaintenancePlanService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenancePlanControllerTest {
    @Test
    void returnsThePlanAsJsonRatherThanJsonNodeProperties() throws Exception {
        PdfUploadReader uploadReader = mock(PdfUploadReader.class);
        MaintenancePlanService service = mock(MaintenancePlanService.class);
        when(uploadReader.read(any())).thenReturn(new byte[]{1});
        when(service.generate(any(), any())).thenReturn(new ObjectMapper().readTree("""
                {"sections":[],"consumables":[]}
                """));

        var response = new MaintenancePlanController(uploadReader, service, new ObjectMapper())
                .generate(new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[]{1}), "model");

        var json = new ObjectMapper().readTree(response.getBody());
        assertEquals("model", json.path("modelCode").asText());
        assertTrue(json.path("plan").path("sections").isArray());
        assertTrue(json.path("plan").path("consumables").isArray());
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
