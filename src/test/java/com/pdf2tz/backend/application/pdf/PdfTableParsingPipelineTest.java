package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.application.pdf.table.PdfTableParsingService;
import com.pdf2tz.backend.application.pdf.table.TableAssembler;
import com.pdf2tz.backend.application.pdf.table.TableCandidateSelector;
import com.pdf2tz.backend.application.pdf.table.TableNormalizer;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import com.pdf2tz.backend.application.ports.PdfTableExtractorPort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfTableParsingPipelineTest {

    private static final byte[] PDF_CONTENT = new byte[]{1, 2, 3};

    @Test
    void parsesTablesUsingDocumentNoiseProfile() {
        RecordingPdfReaderPort readerPort = new RecordingPdfReaderPort(new ExtractedDocument(List.of(
                new ExtractedPage(1, "example.com\nПолезный текст первой страницы"),
                new ExtractedPage(2, "example.com\nПолезный текст второй страницы"),
                new ExtractedPage(3, "example.com\nПолезный текст третьей страницы")
        )));
        RecordingPdfTableExtractorPort tableExtractorPort = new RecordingPdfTableExtractorPort(List.of(
                candidate(
                        area(1, 100, 40, 180, 540),
                        row("Параметр", "Значение example.com"),
                        row("Скорость", "10 мл/ч")
                )
        ));
        PdfTableParsingPipeline pipeline = pipeline(readerPort, tableExtractorPort);

        List<ParsedTable> tables = pipeline.parseTables(PDF_CONTENT);

        assertArrayEquals(PDF_CONTENT, readerPort.receivedContent());
        assertArrayEquals(PDF_CONTENT, tableExtractorPort.receivedContent());
        assertEquals(1, tables.size());
        assertEquals("Значение", cellText(tables.get(0), 0, 1));
        assertEquals("10 мл/ч", cellText(tables.get(0), 1, 1));
    }

    @Test
    void rejectsNullPdfContent() {
        PdfTableParsingPipeline pipeline = pipeline(
                new RecordingPdfReaderPort(new ExtractedDocument(List.of(
                        new ExtractedPage(1, "Текст")
                ))),
                new RecordingPdfTableExtractorPort(List.of())
        );

        assertThrows(
                NullPointerException.class,
                () -> pipeline.parseTables(null)
        );
    }

    private PdfTableParsingPipeline pipeline(
            PdfReaderPort readerPort,
            PdfTableExtractorPort tableExtractorPort
    ) {
        PdfTableParsingService tableParsingService = new PdfTableParsingService(
                tableExtractorPort,
                new TableCandidateSelector(),
                new TableNormalizer(new TextCleaner()),
                new TableAssembler()
        );

        return new PdfTableParsingPipeline(
                readerPort,
                new DocumentNoiseProfileBuilder(),
                tableParsingService
        );
    }

    private String cellText(
            ParsedTable table,
            int rowIndex,
            int cellIndex
    ) {
        return table.rows()
                .get(rowIndex)
                .cells()
                .get(cellIndex)
                .text();
    }

    private static TableCandidate candidate(
            TableArea area,
            TableRow... rows
    ) {
        return new TableCandidate(area, Arrays.asList(rows));
    }

    private static TableArea area(
            int pageNumber,
            double top,
            double left,
            double bottom,
            double right
    ) {
        return new TableArea(pageNumber, top, left, bottom, right);
    }

    private static TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }

    private static class RecordingPdfReaderPort implements PdfReaderPort {

        private final ExtractedDocument document;
        private byte[] receivedContent;

        private RecordingPdfReaderPort(ExtractedDocument document) {
            this.document = document;
        }

        @Override
        public ExtractedDocument read(byte[] content) {
            receivedContent = content;

            return document;
        }

        private byte[] receivedContent() {
            return receivedContent;
        }
    }

    private static class RecordingPdfTableExtractorPort implements PdfTableExtractorPort {

        private final List<TableCandidate> candidates;
        private byte[] receivedContent;

        private RecordingPdfTableExtractorPort(List<TableCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<TableCandidate> extract(byte[] content) {
            receivedContent = content;

            return candidates;
        }

        private byte[] receivedContent() {
            return receivedContent;
        }
    }
}
