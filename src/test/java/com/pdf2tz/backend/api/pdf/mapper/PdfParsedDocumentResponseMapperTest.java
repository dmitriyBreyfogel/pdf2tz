package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfDocumentBlockTypeResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfParsedDocumentResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTableBlockResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTextBlockResponseDto;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PdfParsedDocumentResponseMapperTest {

    private final PdfParsedDocumentResponseMapper mapper = new PdfParsedDocumentResponseMapper(
            new PdfTableResponseMapper()
    );

    @Test
    void mapsParsedDocumentBlocksToResponse() {
        ParsedTable firstTable = table(
                1,
                row("Параметр", "Значение"),
                row("Скорость", "10 мл/ч")
        );
        ParsedTable secondTable = table(
                2,
                row("Объём", "250 мл")
        );
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(
                        new TextBlock("Описание перед таблицей"),
                        new TableBlock(firstTable)
                )),
                new ParsedPage(2, List.of(
                        new TableBlock(secondTable)
                ))
        ));

        PdfParsedDocumentResponseDto response = mapper.toResponse(document);

        assertEquals(2, response.pages().size());
        assertEquals(1, response.pages().get(0).pageNumber());
        assertEquals(2, response.pages().get(0).blocks().size());

        PdfTextBlockResponseDto textBlock = assertInstanceOf(
                PdfTextBlockResponseDto.class,
                response.pages().get(0).blocks().get(0)
        );
        PdfTableBlockResponseDto firstTableBlock = assertInstanceOf(
                PdfTableBlockResponseDto.class,
                response.pages().get(0).blocks().get(1)
        );
        PdfTableBlockResponseDto secondTableBlock = assertInstanceOf(
                PdfTableBlockResponseDto.class,
                response.pages().get(1).blocks().get(0)
        );

        assertEquals(PdfDocumentBlockTypeResponseDto.TEXT, textBlock.type());
        assertEquals("Описание перед таблицей", textBlock.text());
        assertEquals(PdfDocumentBlockTypeResponseDto.TABLE, firstTableBlock.type());
        assertEquals(1, firstTableBlock.table().tableNumber());
        assertEquals(List.of("Скорость", "10 мл/ч"), firstTableBlock.table().rows().get(1));
        assertEquals(2, secondTableBlock.table().tableNumber());
        assertEquals(List.of("Объём", "250 мл"), secondTableBlock.table().rows().get(0));
    }

    private ParsedTable table(
            int pageNumber,
            TableRow... rows
    ) {
        return new ParsedTable(List.of(new TableFragment(
                new TableArea(pageNumber, 100, 40, 200, 540),
                Arrays.asList(rows)
        )));
    }

    private TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }
}
