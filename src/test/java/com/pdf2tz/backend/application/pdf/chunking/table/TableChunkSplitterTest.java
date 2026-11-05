package com.pdf2tz.backend.application.pdf.chunking.table;

import com.pdf2tz.backend.application.pdf.chunking.serialization.DocumentChunkSerializer;
import com.pdf2tz.backend.application.pdf.chunking.serialization.TableTextSerializer;
import com.pdf2tz.backend.application.pdf.chunking.text.TextSegmentSplitter;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.infrastructure.text.IcuTextBoundaryAdapter;
import com.pdf2tz.backend.infrastructure.text.JTokkitTokenizerAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableChunkSplitterTest {

    private final TableTextSerializer tableSerializer = new TableTextSerializer();
    private final DocumentChunkSerializer documentSerializer = new DocumentChunkSerializer();
    private final JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();
    private final TableChunkSplitter splitter = new TableChunkSplitter(
            tableSerializer,
            new TableHeaderDetector(),
            new TextSegmentSplitter(new IcuTextBoundaryAdapter())
    );

    @Test
    void detectsOnlyConfidentHeader() {
        TableHeaderDetector detector = new TableHeaderDetector();
        assertEquals(1, detector.detect(List.of(
                row("Параметр", "Значение"),
                row("Частота", "50 Гц"),
                row("Поток", "10 л/мин")
        )));
        assertEquals(0, detector.detect(List.of(
                row("Показатель", "10"),
                row("Другое", "20"),
                row("Третье", "30")
        )));
    }

    @Test
    void splitsLargeTableByRowsAndKeepsMarkedHeader() {
        List<TableRow> rows = new ArrayList<>();
        rows.add(row("Параметр", "Значение"));
        for (int index = 1; index <= 40; index++) {
            rows.add(row("Параметр " + index, index + " Гц"));
        }
        ParsedTable table = table(rows);
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "1 Operation", 1)));

        List<TableChunkPart> parts = splitter.split(
                table,
                body -> tokenizer.countTokens(documentSerializer.serialize(path, List.of(body))) <= 128
        );

        assertTrue(parts.size() > 1);
        assertTrue(parts.stream().allMatch(part ->
                tokenizer.countTokens(documentSerializer.serialize(path, List.of(part.content()))) <= 128
        ));
        assertTrue(parts.get(1).content().contains("[REPEATED HEADER]"));
        assertTrue(parts.stream().anyMatch(part -> part.content().contains("Параметр 40")));
    }

    @Test
    void preservesVeryLongCellThroughRowFallback() {
        ParsedTable table = table(List.of(
                row("Параметр", "Описание"),
                row("Код", "длинное значение ".repeat(300)),
                row("Код 2", "Обычное значение")
        ));
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "1 Operation", 1)));

        List<TableChunkPart> parts = splitter.split(
                table,
                body -> tokenizer.countTokens(documentSerializer.serialize(path, List.of(body))) <= 128
        );

        assertTrue(parts.size() > 1);
        assertTrue(parts.stream().allMatch(part ->
                tokenizer.countTokens(documentSerializer.serialize(path, List.of(part.content()))) <= 128
        ));
        assertTrue(parts.get(0).content().contains("| Параметр | Описание |"));
        assertTrue(parts.stream().anyMatch(part -> part.content().contains("[COLUMN 2]")));
    }

    @Test
    void preservesEmptyColumnMarkerInOversizedRowFallback() {
        ParsedTable table = table(List.of(
                row("Параметр", "Описание"),
                row("Код ".repeat(150), ""),
                row("Код 2", "Обычное значение")
        ));
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "1 Operation", 1)));

        List<TableChunkPart> parts = splitter.split(
                table,
                body -> tokenizer.countTokens(documentSerializer.serialize(path, List.of(body))) <= 128
        );

        assertTrue(parts.stream().anyMatch(part -> part.content().contains("[COLUMN 2]")));
    }

    @Test
    void rejectsBudgetTooSmallForTablePartInsteadOfReturningUnboundedContent() {
        ParsedTable table = table(List.of(
                row("Параметр", "Описание"),
                row("Код", ""),
                row("Код 2", "Обычное значение")
        ));
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "1 Operation", 1)));

        assertThrows(IllegalArgumentException.class, () -> splitter.split(table,
                body -> tokenizer.countTokens(documentSerializer.serialize(path, List.of(body))) <= 30));
    }

    @Test
    void tracksPhysicalPagesOfSplitMultiPageTable() {
        List<TableRow> firstRows = new ArrayList<>();
        firstRows.add(row("Параметр", "Описание"));
        List<TableRow> secondRows = new ArrayList<>();
        secondRows.add(row("Параметр", "Описание"));
        for (int index = 1; index <= 15; index++) {
            firstRows.add(row("Параметр " + index,
                    "Описание операции на первой странице " + index));
            secondRows.add(row("Параметр " + (index + 15),
                    "Описание операции на второй странице " + index));
        }
        ParsedTable table = new ParsedTable(List.of(
                new TableFragment(new TableArea(4, 10, 10, 500, 500), firstRows),
                new TableFragment(new TableArea(5, 10, 10, 500, 500), secondRows)
        ));
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "Техническое обслуживание", 4)));

        List<TableChunkPart> parts = splitter.split(table,
                body -> tokenizer.countTokens(documentSerializer.serialize(path, List.of(body))) <= 128);

        assertTrue(parts.size() > 2);
        assertTrue(parts.stream().anyMatch(part -> part.sourcePages().equals(PageRange.single(4))));
        assertTrue(parts.stream().anyMatch(part -> part.sourcePages().equals(PageRange.single(5))));
        assertTrue(parts.stream().allMatch(part ->
                tokenizer.countTokens(documentSerializer.serialize(path, List.of(part.content()))) <= 128));
        assertTrue(parts.stream().anyMatch(part -> part.content().contains("Параметр 30")));
    }

    @Test
    void keepsHeaderEvenWhenItCannotFitAsRepeatedContext() {
        ParsedTable table = table(List.of(
                row("Параметр ".repeat(100), "Описание ".repeat(100)),
                row("1", "10"),
                row("2", "20")
        ));
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "Техническое обслуживание", 1)));
        assertEquals(1, new TableHeaderDetector().detect(table.rows()));

        List<TableChunkPart> parts = splitter.split(table,
                body -> tokenizer.countTokens(documentSerializer.serialize(path, List.of(body))) <= 128);

        assertTrue(parts.stream().allMatch(part ->
                tokenizer.countTokens(documentSerializer.serialize(path, List.of(part.content()))) <= 128));
        assertTrue(parts.stream().anyMatch(part -> part.content().contains("Параметр")));
        assertTrue(parts.stream().anyMatch(part -> part.content().contains("Описание")));
        assertTrue(parts.stream().anyMatch(part -> part.content().contains("| 2 | 20 |")));
    }

    private ParsedTable table(List<TableRow> rows) {
        return new ParsedTable(List.of(
                new TableFragment(
                        new TableArea(1, 10, 10, 500, 500),
                        rows
                )
        ));
    }

    private TableRow row(String... values) {
        return new TableRow(java.util.Arrays.stream(values).map(TableCell::new).toList());
    }
}
