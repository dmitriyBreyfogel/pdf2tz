package com.pdf2tz.backend.infrastructure.pdf;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.OcrConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.junit.jupiter.api.Test;
import com.pdf2tz.backend.error.AppException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaPdfReaderTest {

    @Test
    void rejectsPlainTextDisguisedAsPdf() {
        assertThrows(AppException.class, () -> new TikaPdfReader().read(
                "not a pdf".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void readsNativeTextAndPreservesBlankPageWithoutOcr() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            pdf.addPage(new PDPage());
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText("Pressure 15 kPa");
                stream.endText();
            }
            pdf.save(bytes);
            var result = new TikaPdfReader().read(bytes.toByteArray());
            assertEquals(2, result.pages().size());
            assertTrue(result.pages().get(0).text().contains("Pressure 15 kPa"));
            assertTrue(result.pages().get(1).text().isBlank());
        }
    }

    @Test
    void createsPdfParseContextForNativePdfTextLayer() {
        TikaPdfReader reader = new TikaPdfReader();

        ParseContext context = reader.createParseContext();
        PDFParserConfig pdfParserConfig = context.get(PDFParserConfig.class);

        assertNotNull(pdfParserConfig);
        assertNotNull(pdfParserConfig.getOcr());
        assertEquals(OcrConfig.Strategy.NO_OCR, pdfParserConfig.getOcr().getStrategy());
        assertEquals(PDFParserConfig.IMAGE_STRATEGY.NONE, pdfParserConfig.getImageStrategy());
    }
}
