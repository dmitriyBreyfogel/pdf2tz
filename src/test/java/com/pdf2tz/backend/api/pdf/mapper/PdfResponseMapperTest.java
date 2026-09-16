package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.model.document.DocumentBlock;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfResponseMapperTest {

    private final PdfResponseMapper mapper = new PdfResponseMapper();

    @Test
    void mapsParsedDocumentTextBlocksToPdfResponse() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(
                        new TextBlock("Первый блок"),
                        new TextBlock("Второй блок")
                )),
                new ParsedPage(2, List.of())
        ));

        PdfResponseDto response = mapper.toResponse(document);

        assertEquals(2, response.pages().size());
        assertEquals(1, response.pages().get(0).pageNumber());
        assertEquals("Первый блок\n\nВторой блок", response.pages().get(0).text());
        assertEquals(2, response.pages().get(1).pageNumber());
        assertEquals("", response.pages().get(1).text());
    }

    @Test
    void ignoresBlocksThatCurrentTextOnlyApiCannotExpose() {
        DocumentBlock unsupportedBlock = new DocumentBlock() {
        };
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(
                        new TextBlock("Текстовый блок"),
                        unsupportedBlock
                ))
        ));

        PdfResponseDto response = mapper.toResponse(document);

        assertEquals("Текстовый блок", response.pages().get(0).text());
    }
}
