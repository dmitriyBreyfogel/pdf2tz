package com.pdf2tz.backend.application.pdf.model.table;

import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableModelTest {

    @Test
    void tableAreaProvidesGeometryForCandidateComparison() {
        TableArea area = new TableArea(2, 10, 20, 70, 120);
        TableArea overlappingArea = new TableArea(2, 40, 70, 90, 150);
        TableArea areaOnAnotherPage = new TableArea(3, 40, 70, 90, 150);

        assertEquals(100, area.width());
        assertEquals(60, area.height());
        assertEquals(6000, area.areaSize());
        assertTrue(area.intersects(overlappingArea));
        assertEquals(1500, area.intersectionArea(overlappingArea));
        assertEquals(0.375, area.overlapRatio(overlappingArea));
        assertFalse(area.intersects(areaOnAnotherPage));
        assertEquals(0, area.intersectionArea(areaOnAnotherPage));
    }

    @Test
    void tableRowNormalizesCellsAndProvidesPlainText() {
        TableRow row = row(" Значение ", "", " 10 мл ");
        TableRow blankRow = row(" ", "");

        assertEquals(3, row.columnCount());
        assertEquals("Значение |  | 10 мл", row.plainText());
        assertFalse(row.isBlank());
        assertTrue(blankRow.isBlank());
    }

    @Test
    void tableCandidateAllowsIrregularRawRows() {
        TableCandidate candidate = new TableCandidate(
                area(1, 10),
                List.of(
                        row("Колонка 1", "Колонка 2"),
                        row("Значение без второй колонки")
                )
        );

        assertEquals(1, candidate.pageNumber());
        assertEquals(2, candidate.rowCount());
        assertEquals(2, candidate.columnCount());
        assertFalse(candidate.hasConsistentColumnCount());
    }

    @Test
    void tableFragmentRequiresRectangularRows() {
        TableFragment fragment = new TableFragment(
                area(1, 10),
                List.of(
                        row("Колонка 1", "Колонка 2"),
                        row("Значение 1", "Значение 2")
                )
        );

        assertEquals(1, fragment.pageNumber());
        assertEquals(2, fragment.rowCount());
        assertEquals(2, fragment.columnCount());

        assertThrows(IllegalArgumentException.class, () -> new TableFragment(
                area(1, 20),
                List.of(
                        row("Колонка 1", "Колонка 2"),
                        row("Значение без второй колонки")
                )
        ));
    }

    @Test
    void parsedTableCombinesOrderedFragmentsIntoSingleLogicalTable() {
        TableFragment firstFragment = new TableFragment(
                area(4, 100),
                List.of(
                        row("Параметр", "Значение"),
                        row("Скорость", "10 мл/ч")
                )
        );
        TableFragment secondFragment = new TableFragment(
                area(5, 30),
                List.of(
                        row("Объём", "250 мл")
                )
        );

        ParsedTable table = new ParsedTable(List.of(firstFragment, secondFragment));

        assertEquals(4, table.startPageNumber());
        assertEquals(5, table.endPageNumber());
        assertEquals(2, table.columnCount());
        assertEquals(3, table.rows().size());
        assertTrue(table.isMultiPage());
    }

    @Test
    void parsedTableRejectsBrokenLogicalTable() {
        TableFragment firstFragment = new TableFragment(
                area(4, 100),
                List.of(row("Параметр", "Значение"))
        );
        TableFragment previousPageFragment = new TableFragment(
                area(3, 100),
                List.of(row("Объём", "250 мл"))
        );
        TableFragment differentColumnCountFragment = new TableFragment(
                area(5, 100),
                List.of(row("Объём", "250 мл", "Комментарий"))
        );

        assertThrows(IllegalArgumentException.class, () -> new ParsedTable(List.of(
                firstFragment,
                previousPageFragment
        )));
        assertThrows(IllegalArgumentException.class, () -> new ParsedTable(List.of(
                firstFragment,
                differentColumnCountFragment
        )));
    }

    @Test
    void tableBlockContainsCompleteParsedTable() {
        ParsedTable table = new ParsedTable(List.of(
                new TableFragment(
                        area(1, 10),
                        List.of(row("Параметр", "Значение"))
                )
        ));

        TableBlock block = new TableBlock(table);

        assertEquals(table, block.table());
        assertThrows(NullPointerException.class, () -> new TableBlock(null));
    }

    private TableArea area(int pageNumber, double top) {
        return new TableArea(pageNumber, top, 20, top + 40, 120);
    }

    private TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }
}
