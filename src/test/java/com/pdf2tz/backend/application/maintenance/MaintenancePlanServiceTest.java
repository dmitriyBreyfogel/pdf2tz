package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaintenancePlanServiceTest {
    @Test
    void sendsOnlyInternallySelectedMaintenanceContextToPort() throws Exception {
        PdfChunkingPipeline pipeline = mock(PdfChunkingPipeline.class);
        RouterAiClient client = mock(RouterAiClient.class);
        when(pipeline.chunk(any())).thenReturn(new ChunkedDocument(List.of(
                new DocumentChunk(1, SectionPath.root(), PageRange.single(1), "Содержание", 3),
                new DocumentChunk(2, SectionPath.root(), PageRange.single(14), "Техническое обслуживание: очищайте прибор еженедельно.", 12)
        )));
        var expected = new ObjectMapper().readTree("{\"sections\":[],\"consumables\":[]}");
        when(client.generate(eq(MaintenanceLlmModel.DEEPSEEK_FLASH), anyString(), anyString())).thenReturn(expected);

        var result = new MaintenancePlanService(pipeline, client).generate(new byte[]{1}, "deepseek/deepseek-v4.1-flash");

        assertEquals(expected, result);
        verify(client).generate(eq(MaintenanceLlmModel.DEEPSEEK_FLASH), anyString(), contains("CHUNK 2"));
        verify(client, never()).generate(any(), anyString(), contains("CHUNK 1"));
    }
}
