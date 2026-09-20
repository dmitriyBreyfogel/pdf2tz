package com.pdf2tz.backend.api;

import com.pdf2tz.backend.api.pdf.PdfController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class PdfUploadErrorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(mock(PdfController.class))
            .setControllerAdvice(handler).build();

    @Test
    void missingFileAndMissingMultipartBodyAreClientErrorsForEveryEndpoint() throws Exception {
        for (String endpoint : new String[]{"extract", "tables", "parsed"}) {
            mvc.perform(post("/api/v1/pdf/" + endpoint))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_REQUEST"));
            mvc.perform(multipart("/api/v1/pdf/" + endpoint))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_REQUEST"));
        }
    }

    @Test
    void keepsPayloadTooLargeDistinctFromMalformedRequest() {
        var response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(200));
        assertEquals(413, response.getStatusCode().value());
        assertEquals("FILE_TOO_LARGE", response.getBody().code());
    }
}
