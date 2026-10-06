package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.retrieval.LexicalChunkRanker;
import com.pdf2tz.backend.application.pdf.retrieval.PdfContextAssembler;
import com.pdf2tz.backend.application.pdf.retrieval.RetrievalProperties;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.infrastructure.text.JTokkitTokenizerAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfContextPreparationPipelineTest {

    private final PdfChunkingPipeline chunkingPipeline = mock(PdfChunkingPipeline.class);
    private final RetrievalProperties properties = new RetrievalProperties();
    private final JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();
    private final PdfContextPreparationPipeline pipeline = new PdfContextPreparationPipeline(
            chunkingPipeline,
            new LexicalChunkRanker(),
            new PdfContextAssembler(properties, tokenizer),
            tokenizer,
            properties
    );

    @Test
    void preparesContextFromOneChunkingRunAndKeepsSourcePage() {
        byte[] pdf = {1, 2, 3};
        when(chunkingPipeline.chunk(pdf)).thenReturn(new ChunkedDocument(List.of(
                new DocumentChunk(1, SectionPath.root(), PageRange.single(4),
                        "Проверка датчика давления", 10),
                new DocumentChunk(2, SectionPath.root(), PageRange.single(7),
                        "Техническое обслуживание: замена фильтра через 6 месяцев", 20)
        )));

        var result = pipeline.prepare(pdf, "  Какова периодичность замены фильтра?  ");

        verify(chunkingPipeline).chunk(pdf);
        assertEquals("Какова периодичность замены фильтра?", result.prompt());
        assertEquals(2, result.totalChunkCount());
        assertTrue(result.selectedChunks().stream().anyMatch(chunk ->
                chunk.pageRange().startPageNumber() == 7));
        assertTrue(result.preparedPrompt().contains("pages=7-7"));
    }

    @Test
    void rejectsInvalidPromptBeforeParsingPdf() {
        byte[] pdf = {1};
        assertThrows(AppException.class, () -> pipeline.prepare(pdf, "   "));
        assertThrows(AppException.class, () -> pipeline.prepare(pdf, "?"));
        properties.setMaxPromptTokens(1);
        assertThrows(AppException.class, () -> pipeline.prepare(pdf, "Подготовь регламент обслуживания"));
        verify(chunkingPipeline, never()).chunk(pdf);
    }
}
