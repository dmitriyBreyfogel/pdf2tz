package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfResponseMapperTest {

    private final PdfResponseMapper mapper = new PdfResponseMapper();

    @Test
    void preservesTableTextAndEmptyPages() {
        CleanedTextDocument document = new CleanedTextDocument(List.of(
                new CleanedTextPage(1, "Параметр Значение\nСкорость 10 мл/ч"),
                new CleanedTextPage(2, "")
        ));

        PdfResponseDto response = mapper.toResponse(document);

        assertEquals(2, response.pages().size());
        assertEquals(1, response.pages().get(0).pageNumber());
        assertEquals("Параметр Значение\nСкорость 10 мл/ч", response.pages().get(0).text());
        assertEquals(2, response.pages().get(1).pageNumber());
        assertEquals("", response.pages().get(1).text());
    }

}
