package com.pdf2tz.backend.infrastructure.pdf;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TikaPdfReaderTest {

    @Test
    void createsPdfParseContextForNativePdfTextLayer() {
        TikaPdfReader reader = new TikaPdfReader();

        ParseContext context = reader.createParseContext();
        PDFParserConfig pdfParserConfig = context.get(PDFParserConfig.class);

        assertNotNull(pdfParserConfig);
        assertEquals(PDFParserConfig.IMAGE_STRATEGY.NONE, pdfParserConfig.getImageStrategy());
    }
}
