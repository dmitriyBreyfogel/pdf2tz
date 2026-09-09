package com.pdf2tz.backend.application.pdf.assembly;

import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedPage;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedDocumentAssemblerTest {

    private final ParsedDocumentAssembler assembler = new ParsedDocumentAssembler();

    @Test
    void convertsCleanedPagesToParsedPagesWithTextBlocks() {
        CleanedDocument cleanedDocument = new CleanedDocument(List.of(
                new CleanedPage(1, "Первый очищенный текст"),
                new CleanedPage(2, "  Второй очищенный текст  ")
        ));

        ParsedDocument parsedDocument = assembler.assemble(cleanedDocument);

        assertEquals(2, parsedDocument.pages().size());
        assertEquals(1, parsedDocument.pages().get(0).pageNumber());
        assertEquals(2, parsedDocument.pages().get(1).pageNumber());
        assertEquals(1, parsedDocument.pages().get(0).blocks().size());
        assertEquals(1, parsedDocument.pages().get(1).blocks().size());

        TextBlock firstBlock = assertInstanceOf(
                TextBlock.class,
                parsedDocument.pages().get(0).blocks().get(0)
        );
        TextBlock secondBlock = assertInstanceOf(
                TextBlock.class,
                parsedDocument.pages().get(1).blocks().get(0)
        );

        assertEquals("Первый очищенный текст", firstBlock.text());
        assertEquals("Второй очищенный текст", secondBlock.text());
    }

    @Test
    void preservesBlankPagesWithoutCreatingEmptyBlocks() {
        CleanedDocument cleanedDocument = new CleanedDocument(List.of(
                new CleanedPage(1, ""),
                new CleanedPage(2, "   "),
                new CleanedPage(3, "Полезный текст")
        ));

        ParsedDocument parsedDocument = assembler.assemble(cleanedDocument);

        assertEquals(3, parsedDocument.pages().size());
        assertTrue(parsedDocument.pages().get(0).blocks().isEmpty());
        assertTrue(parsedDocument.pages().get(1).blocks().isEmpty());
        assertEquals(1, parsedDocument.pages().get(2).blocks().size());
    }
}
