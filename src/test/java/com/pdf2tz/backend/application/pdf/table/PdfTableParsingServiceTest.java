package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.application.ports.PdfTableExtractorPort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTableParsingServiceTest {

    private static final byte[] PDF_CONTENT = new byte[]{1, 2, 3};

    @Test
    void parsesPdfTablesThroughApplicationFlow() {
        RecordingPdfTableExtractorPort extractorPort = new RecordingPdfTableExtractorPort(List.of(
                candidate(
                        area(1, 500, 40, 780, 540),
                        row("Параметр", "Значение example.com"),
                        row("watermark", "watermark"),
                        row("Скорость", "10 мл/ч")
                ),
                candidate(
                        area(2, 40, 42, 220, 538),
                        row("Объём", "250 мл")
                ),
                candidate(
                        area(3, 20, 40, 80, 540),
                        row("Обычный текст без таблицы")
                )
        ));
        PdfTableParsingService service = service(extractorPort);
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("watermark"),
                Set.of("example.com")
        );

        List<ParsedTable> tables = service.parseTables(PDF_CONTENT, noiseProfile);

        assertArrayEquals(PDF_CONTENT, extractorPort.receivedContent());
        assertEquals(1, tables.size());

        ParsedTable table = tables.get(0);

        assertTrue(table.isMultiPage());
        assertEquals(1, table.startPageNumber());
        assertEquals(2, table.endPageNumber());
        assertEquals(3, table.rows().size());
        assertEquals("Параметр", cellText(table, 0, 0));
        assertEquals("Значение", cellText(table, 0, 1));
        assertEquals("Скорость", cellText(table, 1, 0));
        assertEquals("10 мл/ч", cellText(table, 1, 1));
        assertEquals("Объём", cellText(table, 2, 0));
        assertEquals("250 мл", cellText(table, 2, 1));
    }

    @Test
    void returnsEmptyTableListWhenExtractorDoesNotFindCandidates() {
        PdfTableParsingService service = service(new RecordingPdfTableExtractorPort(List.of()));

        List<ParsedTable> tables = service.parseTables(
                PDF_CONTENT,
                new DocumentNoiseProfile(Set.of(), Set.of())
        );

        assertTrue(tables.isEmpty());
    }

    @Test
    void rejectsNullPdfContent() {
        PdfTableParsingService service = service(new RecordingPdfTableExtractorPort(List.of()));

        assertThrows(
                NullPointerException.class,
                () -> service.parseTables(null, new DocumentNoiseProfile(Set.of(), Set.of()))
        );
    }

    @Test
    void rejectsNullNoiseProfile() {
        PdfTableParsingService service = service(new RecordingPdfTableExtractorPort(List.of()));

        assertThrows(
                NullPointerException.class,
                () -> service.parseTables(PDF_CONTENT, null)
        );
    }

    private PdfTableParsingService service(PdfTableExtractorPort extractorPort) {
        return new PdfTableParsingService(
                extractorPort,
                new TableCandidateSelector(),
                new TableNormalizer(new TextCleaner()),
                new TableAssembler()
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
