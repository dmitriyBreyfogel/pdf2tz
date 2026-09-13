package com.pdf2tz.backend.application.pdf.assembly;

import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedPage;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    @Test
    void addsTableBlocksToTableStartPages() {
        CleanedDocument cleanedDocument = new CleanedDocument(List.of(
                new CleanedPage(1, "Первая страница"),
                new CleanedPage(2, "Вторая страница")
        ));
        ParsedTable firstPageTable = table(1, 300, 40);
        ParsedTable secondPageTable = table(2, 100, 40);

        ParsedDocument parsedDocument = assembler.assemble(
                cleanedDocument,
                List.of(secondPageTable, firstPageTable)
        );

        assertEquals(2, parsedDocument.pages().get(0).blocks().size());
        assertEquals(2, parsedDocument.pages().get(1).blocks().size());
        assertInstanceOf(TextBlock.class, parsedDocument.pages().get(0).blocks().get(0));
        assertInstanceOf(TextBlock.class, parsedDocument.pages().get(1).blocks().get(0));

        TableBlock firstTableBlock = assertInstanceOf(
                TableBlock.class,
                parsedDocument.pages().get(0).blocks().get(1)
        );
        TableBlock secondTableBlock = assertInstanceOf(
                TableBlock.class,
                parsedDocument.pages().get(1).blocks().get(1)
        );

        assertEquals(firstPageTable, firstTableBlock.table());
        assertEquals(secondPageTable, secondTableBlock.table());
    }

    @Test
    void sortsTableBlocksByReadingOrderWithinPage() {
        CleanedDocument cleanedDocument = new CleanedDocument(List.of(
                new CleanedPage(1, "")
        ));
        ParsedTable lowerTable = table(1, 300, 40);
        ParsedTable upperTable = table(1, 100, 40);

        ParsedDocument parsedDocument = assembler.assemble(
                cleanedDocument,
                List.of(lowerTable, upperTable)
        );

        assertEquals(2, parsedDocument.pages().get(0).blocks().size());

        TableBlock firstBlock = assertInstanceOf(
                TableBlock.class,
                parsedDocument.pages().get(0).blocks().get(0)
        );
        TableBlock secondBlock = assertInstanceOf(
                TableBlock.class,
                parsedDocument.pages().get(0).blocks().get(1)
        );

        assertEquals(upperTable, firstBlock.table());
        assertEquals(lowerTable, secondBlock.table());
    }

    private ParsedTable table(
            int pageNumber,
            double top,
            double left
    ) {
        return new ParsedTable(List.of(new TableFragment(
                new TableArea(pageNumber, top, left, top + 100, left + 200),
                List.of(row("Параметр", "Значение"))
        )));
    }

    private TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }
}
