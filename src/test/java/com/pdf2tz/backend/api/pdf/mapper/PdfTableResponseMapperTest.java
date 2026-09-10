package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfTablesResponseDto;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfTableResponseMapperTest {

    private final PdfTableResponseMapper mapper = new PdfTableResponseMapper();

    @Test
    void mapsParsedTablesToResponse() {
        ParsedTable table = new ParsedTable(List.of(
                fragment(
                        area(2, 500, 40, 780, 540),
                        row("Параметр", "Значение"),
                        row("Скорость", "10 мл/ч")
                ),
                fragment(
                        area(3, 40, 42, 220, 538),
                        row("Объём", "250 мл")
                )
        ));

        PdfTablesResponseDto response = mapper.toResponse(List.of(table));

        assertEquals(1, response.tables().size());
        assertEquals(1, response.tables().get(0).tableNumber());
        assertEquals(2, response.tables().get(0).startPageNumber());
        assertEquals(3, response.tables().get(0).endPageNumber());
        assertEquals(2, response.tables().get(0).columnCount());
        assertEquals(2, response.tables().get(0).fragments().size());
        assertEquals(500, response.tables().get(0).fragments().get(0).area().top());
        assertEquals(500, response.tables().get(0).fragments().get(0).area().width());
        assertEquals(List.of("Скорость", "10 мл/ч"), response.tables().get(0).rows().get(1));
        assertEquals(List.of("Объём", "250 мл"), response.tables().get(0).rows().get(2));
    }

    private TableFragment fragment(
            TableArea area,
            TableRow... rows
    ) {
        return new TableFragment(area, Arrays.asList(rows));
    }

    private TableArea area(
            int pageNumber,
            double top,
            double left,
            double bottom,
            double right
    ) {
        return new TableArea(pageNumber, top, left, bottom, right);
    }

    private TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }
}
