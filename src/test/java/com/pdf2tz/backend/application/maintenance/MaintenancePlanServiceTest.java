package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        var expected = new ObjectMapper().readTree("""
                {"sections":[{"name":"x","rows":[{"number":1,"deviceName":"Прибор","maintenanceType":"Очистка","maintenanceWorkDescription":["Очистить."],"registrationNumber":null,"inventoryNumber":"forbidden","frequency":"Еженедельно","sourcePages":[14]}]}],"consumables":[]}
                """);
        when(client.generate(eq(MaintenanceLlmModel.GPT_LUNA), anyString(), anyString())).thenReturn(expected);

        var result = new MaintenancePlanService(pipeline, client, new MaintenanceChunkSelector(chunks -> Set.of()), new MaintenancePlanResponseNormalizer(), new MaintenancePlanDiagnosticsProperties()).generate(new byte[]{1}, "openai/gpt-5.6-luna");

        assertEquals(true, result.path("sections").path(0).path("rows").path(0).path("inventoryNumber").isNull());
        var instruction = ArgumentCaptor.forClass(String.class);
        verify(client).generate(eq(MaintenanceLlmModel.GPT_LUNA), instruction.capture(), contains("CHUNK 2"));
        verify(client, never()).generate(any(), anyString(), contains("CHUNK 1"));
        assertTrue(instruction.getValue().contains("Корневой объект содержит РОВНО два поля: sections и consumables"));
        assertTrue(instruction.getValue().contains("Не отправляй частичный JSON"));
    }
}
