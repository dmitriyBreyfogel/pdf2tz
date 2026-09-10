package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableAssemblerTest {

    private final TableAssembler assembler = new TableAssembler();

    @Test
    void returnsTablesInReadingOrder() {
        TableFragment secondPageFragment = fragment(area(2, 20, 30, 100, 240), row("E", "F"));
        TableFragment lowerFragment = fragment(area(1, 200, 30, 260, 240), row("C", "D"));
        TableFragment upperFragment = fragment(area(1, 20, 30, 100, 240), row("A", "B"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                secondPageFragment,
                lowerFragment,
                upperFragment
        ));

        assertEquals(3, tables.size());
        assertEquals(upperFragment, tables.get(0).fragments().get(0));
        assertEquals(lowerFragment, tables.get(1).fragments().get(0));
        assertEquals(secondPageFragment, tables.get(2).fragments().get(0));
    }

    @Test
    void mergesAlignedFragmentsFromAdjacentPages() {
        TableFragment firstFragment = fragment(
                area(3, 500, 40, 780, 540),
                row("Параметр", "Значение"),
                row("Скорость", "10 мл/ч")
        );
        TableFragment continuationFragment = fragment(
                area(4, 40, 43, 240, 537),
                row("Объём", "250 мл")
        );

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                continuationFragment
        ));

        assertEquals(1, tables.size());
        assertTrue(tables.get(0).isMultiPage());
        assertEquals(List.of(firstFragment, continuationFragment), tables.get(0).fragments());
        assertEquals(3, tables.get(0).startPageNumber());
        assertEquals(4, tables.get(0).endPageNumber());
        assertEquals(3, tables.get(0).rows().size());
    }

    @Test
    void mergesContinuationChainAcrossSeveralPages() {
        TableFragment firstFragment = fragment(area(1, 500, 40, 780, 540), row("A", "B"));
        TableFragment secondFragment = fragment(area(2, 40, 40, 780, 540), row("C", "D"));
        TableFragment thirdFragment = fragment(area(3, 40, 40, 220, 540), row("E", "F"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                thirdFragment,
                firstFragment,
                secondFragment
        ));

        assertEquals(1, tables.size());
        assertEquals(List.of(firstFragment, secondFragment, thirdFragment), tables.get(0).fragments());
        assertEquals(3, tables.get(0).rows().size());
    }

    @Test
    void doesNotMergeFragmentsOnSamePage() {
        TableFragment firstFragment = fragment(area(1, 20, 30, 100, 240), row("A", "B"));
        TableFragment secondFragment = fragment(area(1, 200, 30, 260, 240), row("C", "D"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                secondFragment
        ));

        assertEquals(2, tables.size());
        assertFalse(tables.get(0).isMultiPage());
        assertFalse(tables.get(1).isMultiPage());
    }

    @Test
    void doesNotMergeFragmentsWhenPageGapExists() {
        TableFragment firstFragment = fragment(area(1, 500, 40, 780, 540), row("A", "B"));
        TableFragment laterFragment = fragment(area(3, 40, 40, 220, 540), row("C", "D"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                laterFragment
        ));

        assertEquals(2, tables.size());
    }

    @Test
    void doesNotMergeFragmentsWithDifferentColumnCount() {
        TableFragment firstFragment = fragment(area(1, 500, 40, 780, 540), row("A", "B"));
        TableFragment differentColumnCountFragment = fragment(area(2, 40, 40, 220, 540), row("C", "D", "E"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                differentColumnCountFragment
        ));

        assertEquals(2, tables.size());
    }

    @Test
    void doesNotMergeFragmentsWithDifferentHorizontalBounds() {
        TableFragment firstFragment = fragment(area(1, 500, 40, 780, 540), row("A", "B"));
        TableFragment shiftedFragment = fragment(area(2, 40, 120, 220, 620), row("C", "D"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                shiftedFragment
        ));

        assertEquals(2, tables.size());
    }

    @Test
    void doesNotMergeAdjacentFragmentsWhenPreviousIsNotNearPageBottom() {
        TableFragment firstFragment = fragment(area(1, 200, 40, 300, 540), row("A", "B"));
        TableFragment nextPageFragment = fragment(area(2, 40, 40, 220, 540), row("C", "D"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                nextPageFragment
        ));

        assertEquals(2, tables.size());
    }

    @Test
    void doesNotMergeAdjacentFragmentsWhenCurrentIsNotNearPageTop() {
        TableFragment firstFragment = fragment(area(1, 500, 40, 780, 540), row("A", "B"));
        TableFragment nextPageFragment = fragment(area(2, 260, 40, 420, 540), row("C", "D"));

        List<ParsedTable> tables = assembler.assemble(List.of(
                firstFragment,
                nextPageFragment
        ));

        assertEquals(2, tables.size());
    }

    @Test
    void assemblesEmptyInputToEmptyResult() {
        List<ParsedTable> tables = assembler.assemble(List.of());

        assertTrue(tables.isEmpty());
    }

    @Test
    void returnsImmutableTableList() {
        TableFragment fragment = fragment(area(1, 20, 30, 100, 240), row("A", "B"));

        List<ParsedTable> tables = assembler.assemble(List.of(fragment));

        assertThrows(
                UnsupportedOperationException.class,
                () -> tables.add(new ParsedTable(List.of(fragment)))
        );
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
