package com.pdf2tz.backend.application.pdf.retrieval;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.infrastructure.text.JTokkitTokenizerAdapter;
import com.pdf2tz.backend.error.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfContextAssemblerTest {

    private final JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();

    @Test
    void selectsByRelevanceButPresentsSourcesInPdfOrderWithinExactTokenBudget() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setMaxContextTokens(256);
        properties.setMaxChunks(2);
        PdfContextAssembler assembler = new PdfContextAssembler(properties, tokenizer);
        DocumentChunk later = chunk(7, 14, "Техническое обслуживание: проверка датчика");
        DocumentChunk earlier = chunk(2, 4, "Проверка и очистка фильтра");
        DocumentChunk last = chunk(9, 20, "Замена кабеля питания");

        var result = assembler.assemble("Какие операции ТО проводить?", List.of(
                new LexicalChunkRanker.RankedChunk(later, 3),
                new LexicalChunkRanker.RankedChunk(earlier, 2),
                new LexicalChunkRanker.RankedChunk(last, 1)
        ), 12);

        assertEquals(List.of(2, 7), result.selectedChunks().stream()
                .map(DocumentChunk::chunkNumber).toList());
        assertTrue(result.preparedPrompt().indexOf("chunk=2") < result.preparedPrompt().indexOf("chunk=7"));
        assertTrue(result.preparedPrompt().contains("pages=14-14"));
        assertEquals(tokenizer.countTokens(result.preparedPrompt()), result.estimatedTokenCount());
        assertTrue(result.estimatedTokenCount() <= properties.maxContextTokens());
        assertTrue(result.contextAvailable());
    }

    @Test
    void returnsExplicitNoMatchAndRejectsBudgetThatCannotFitAnyChunk() {
        RetrievalProperties properties = new RetrievalProperties();
        PdfContextAssembler assembler = new PdfContextAssembler(properties, tokenizer);
        var noMatch = assembler.assemble("Регламент ТО", List.of(), 8);

        assertFalse(noMatch.contextAvailable());
        assertEquals("", noMatch.preparedPrompt());
        assertEquals(8, noMatch.totalChunkCount());

        properties.setMaxContextTokens(128);
        DocumentChunk oversized = chunk(1, 1, "длинное содержание ".repeat(200));
        assertThrows(AppException.class, () -> assembler.assemble("Запрос", List.of(
                new LexicalChunkRanker.RankedChunk(oversized, 1)), 1));
    }

    @Test
    void skipsOversizedTopMatchAndUsesNextFittingSource() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setMaxContextTokens(128);
        PdfContextAssembler assembler = new PdfContextAssembler(properties, tokenizer);
        DocumentChunk oversized = chunk(1, 1, "длинное содержание ".repeat(200));
        DocumentChunk fitting = chunk(2, 2, "Проверка давления");

        var result = assembler.assemble("Как проверить давление?", List.of(
                new LexicalChunkRanker.RankedChunk(oversized, 2),
                new LexicalChunkRanker.RankedChunk(fitting, 1)
        ), 2);

        assertEquals(List.of(2), result.selectedChunks().stream()
                .map(DocumentChunk::chunkNumber).toList());
        assertTrue(result.estimatedTokenCount() <= properties.maxContextTokens());
    }

    private DocumentChunk chunk(int number, int page, String text) {
        return new DocumentChunk(number, SectionPath.root(), PageRange.single(page), text, 20);
    }
}
