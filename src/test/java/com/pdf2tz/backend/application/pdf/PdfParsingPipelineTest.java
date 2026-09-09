package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.assembly.ParsedDocumentAssembler;
import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PdfParsingPipelineTest {

    @Test
    void parsesPdfContentToParsedDocument() {
        PdfParsingPipeline pipeline = new PdfParsingPipeline(
                new StubPdfReaderPort(new ExtractedDocument(List.of(
                        new ExtractedPage(1, "Полезный текст страницы")
                ))),
                new DocumentNoiseProfileBuilder(),
                new TextCleaner(),
                new ParsedDocumentAssembler()
        );

        ParsedDocument parsedDocument = pipeline.parse(new byte[]{1, 2, 3});

        assertEquals(1, parsedDocument.pages().size());
        assertEquals(1, parsedDocument.pages().get(0).blocks().size());

        TextBlock block = assertInstanceOf(
                TextBlock.class,
                parsedDocument.pages().get(0).blocks().get(0)
        );

        assertEquals("Полезный текст страницы", block.text());
    }

    private record StubPdfReaderPort(
            ExtractedDocument document
    ) implements PdfReaderPort {

        @Override
        public ExtractedDocument read(byte[] content) {
            return document;
        }
    }
}
