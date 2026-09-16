package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.assembly.ParsedDocumentAssembler;
import com.pdf2tz.backend.application.pdf.assembly.TextTableOverlapCleaner;
import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.ExtractedTextDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedTextPage;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.application.pdf.table.PdfTableParsingService;
import com.pdf2tz.backend.application.pdf.table.TableAssembler;
import com.pdf2tz.backend.application.pdf.table.TableCandidateSelector;
import com.pdf2tz.backend.application.pdf.table.TableNormalizer;
import com.pdf2tz.backend.application.pdf.table.TableQualityFilter;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import com.pdf2tz.backend.application.ports.PdfTableExtractorPort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PdfParsingPipelineTest {

    @Test
    void parsesPdfContentToParsedDocument() {
        byte[] content = new byte[]{1, 2, 3};
        RecordingPdfTableExtractorPort tableExtractorPort = new RecordingPdfTableExtractorPort(List.of(
                candidate(
                        area(1, 100, 40, 180, 540),
                        row("Параметр", "Значение"),
                        row("Скорость", "10 мл/ч")
                )
        ));
        PdfParsingPipeline pipeline = new PdfParsingPipeline(
                new StubPdfReaderPort(new ExtractedTextDocument(List.of(
                        new ExtractedTextPage(1, "Полезный текст страницы")
                ))),
                new DocumentNoiseProfileBuilder(),
                new TextCleaner(),
                tableParsingService(tableExtractorPort),
                new ParsedDocumentAssembler(new TextTableOverlapCleaner())
        );

        ParsedDocument parsedDocument = pipeline.parse(content);

        assertEquals(1, parsedDocument.pages().size());
        assertEquals(2, parsedDocument.pages().get(0).blocks().size());

        TextBlock block = assertInstanceOf(
                TextBlock.class,
                parsedDocument.pages().get(0).blocks().get(0)
        );
        TableBlock tableBlock = assertInstanceOf(
                TableBlock.class,
                parsedDocument.pages().get(0).blocks().get(1)
        );

        assertEquals("Полезный текст страницы", block.text());
        assertEquals(2, tableBlock.table().rows().size());
        assertEquals("Параметр", cellText(tableBlock.table(), 0, 0));
        assertEquals("Значение", cellText(tableBlock.table(), 0, 1));
        assertEquals("Скорость", cellText(tableBlock.table(), 1, 0));
        assertEquals("10 мл/ч", cellText(tableBlock.table(), 1, 1));
    }

    private record StubPdfReaderPort(
            ExtractedTextDocument document
    ) implements PdfReaderPort {

        @Override
        public ExtractedTextDocument read(byte[] content) {
            return document;
        }
    }

    private PdfTableParsingService tableParsingService(PdfTableExtractorPort tableExtractorPort) {
        return new PdfTableParsingService(
                tableExtractorPort,
                new TableCandidateSelector(),
                new TableNormalizer(new TextCleaner()),
                new TableQualityFilter(),
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

        private RecordingPdfTableExtractorPort(List<TableCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<TableCandidate> extract(byte[] content) {
            return candidates;
        }
    }
}
