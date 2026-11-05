package com.pdf2tz.backend.infrastructure.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabulaPdfTableExtractorTest {

    private final TabulaPdfTableExtractor extractor = new TabulaPdfTableExtractor();

    @Test
    void extractsTableCandidatesFromPdfWithTextLayer() throws IOException {
        byte[] content = pdfWithSimpleTable();

        List<TableCandidate> candidates = extractor.extract(content);

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream()
                .flatMap(candidate -> candidate.rows().stream())
                .anyMatch(row -> row.plainText().contains("Parameter")));
        assertTrue(candidates.stream()
                .flatMap(candidate -> candidate.rows().stream())
                .anyMatch(row -> row.plainText().contains("Speed")));
    }

    private byte[] pdfWithSimpleTable() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                drawTableGrid(contentStream);
                drawText(contentStream, "Parameter", 90, 680);
                drawText(contentStream, "Value", 260, 680);
                drawText(contentStream, "Speed", 90, 640);
                drawText(contentStream, "10 ml/h", 260, 640);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);

            return output.toByteArray();
        }
    }

    private void drawTableGrid(PDPageContentStream contentStream) throws IOException {
        float left = 80;
        float right = 420;
        float top = 700;
        float middleX = 250;
        float middleY = 660;
        float bottom = 620;

        drawLine(contentStream, left, top, right, top);
        drawLine(contentStream, left, middleY, right, middleY);
        drawLine(contentStream, left, bottom, right, bottom);
        drawLine(contentStream, left, top, left, bottom);
        drawLine(contentStream, middleX, top, middleX, bottom);
        drawLine(contentStream, right, top, right, bottom);
    }

    private void drawLine(
            PDPageContentStream contentStream,
            float startX,
            float startY,
            float endX,
            float endY
    ) throws IOException {
        contentStream.moveTo(startX, startY);
        contentStream.lineTo(endX, endY);
        contentStream.stroke();
    }

    private void drawText(
            PDPageContentStream contentStream,
            String text,
            float x,
            float y
    ) throws IOException {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
}
