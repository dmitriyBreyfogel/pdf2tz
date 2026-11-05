package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableCandidateSelectorTest {

    private final TableCandidateSelector selector = new TableCandidateSelector();

    @Test
    void returnsCandidatesInReadingOrder() {
        TableCandidate secondPageCandidate = candidate(area(2, 10, 20, 80, 200), row("A", "B"));
        TableCandidate lowerCandidate = candidate(area(1, 120, 20, 180, 200), row("A", "B"));
        TableCandidate leftCandidate = candidate(area(1, 10, 20, 80, 200), row("A", "B"));
        TableCandidate rightCandidate = candidate(area(1, 10, 250, 80, 400), row("A", "B"));

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                secondPageCandidate,
                rightCandidate,
                lowerCandidate,
                leftCandidate
        ));

        assertEquals(List.of(
                leftCandidate,
                rightCandidate,
                lowerCandidate,
                secondPageCandidate
        ), selectedCandidates);
    }

    @Test
    void filtersWeakCandidates() {
        TableCandidate oneColumnCandidate = candidate(area(1, 10, 20, 80, 200), row("Обычный абзац"));
        TableCandidate blankCandidate = candidate(area(1, 90, 20, 140, 200), row("", ""));
        TableCandidate meaningfulCandidate = candidate(area(1, 150, 20, 200, 200), row("Параметр", "Значение"));

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                oneColumnCandidate,
                blankCandidate,
                meaningfulCandidate
        ));

        assertEquals(List.of(meaningfulCandidate), selectedCandidates);
    }

    @Test
    void keepsSeparateNonOverlappingCandidates() {
        TableCandidate firstCandidate = candidate(area(1, 10, 20, 80, 200), row("A", "B"));
        TableCandidate secondCandidate = candidate(area(1, 100, 20, 170, 200), row("C", "D"));
        TableCandidate thirdCandidate = candidate(area(2, 10, 20, 80, 200), row("E", "F"));

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                secondCandidate,
                thirdCandidate,
                firstCandidate
        ));

        assertEquals(List.of(
                firstCandidate,
                secondCandidate,
                thirdCandidate
        ), selectedCandidates);
    }

    @Test
    void replacesDuplicateWithBetterCandidate() {
        TableCandidate weakCandidate = candidate(
                area(1, 10, 20, 100, 220),
                row("Параметр", "Значение"),
                row("Скорость", "")
        );
        TableCandidate betterCandidate = candidate(
                area(1, 12, 22, 98, 218),
                row("Параметр", "Значение"),
                row("Скорость", "10 мл/ч"),
                row("Объём", "250 мл")
        );

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                weakCandidate,
                betterCandidate
        ));

        assertEquals(List.of(betterCandidate), selectedCandidates);
    }

    @Test
    void keepsCandidateWithStableColumnCountWhenRowCountIsEqual() {
        TableCandidate irregularCandidate = candidate(
                area(1, 10, 20, 100, 220),
                row("Параметр", "Значение"),
                row("Скорость")
        );
        TableCandidate stableCandidate = candidate(
                area(1, 12, 22, 98, 218),
                row("Параметр", "Значение"),
                row("Скорость", "")
        );

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                irregularCandidate,
                stableCandidate
        ));

        assertEquals(List.of(stableCandidate), selectedCandidates);
    }

    @Test
    void ignoresBlankRowsWhenComparingDuplicateQuality() {
        TableCandidate candidateWithBlankRow = candidate(
                area(1, 10, 20, 100, 220),
                row("Параметр", "Значение"),
                row("", "")
        );
        TableCandidate candidateWithUsefulRow = candidate(
                area(1, 12, 22, 98, 218),
                row("Параметр", "Значение"),
                row("Скорость", "10 мл/ч")
        );

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                candidateWithBlankRow,
                candidateWithUsefulRow
        ));

        assertEquals(List.of(candidateWithUsefulRow), selectedCandidates);
    }

    @Test
    void skipsWideCandidateThatOverlapsMultipleSelectedCandidates() {
        TableCandidate firstTable = candidate(area(1, 10, 20, 80, 200), row("A", "B"));
        TableCandidate secondTable = candidate(area(1, 120, 20, 190, 200), row("C", "D"));
        TableCandidate mergedCandidate = candidate(
                area(1, 5, 10, 200, 220),
                row("A", "B"),
                row("C", "D"),
                row("E", "F")
        );

        List<TableCandidate> selectedCandidates = selector.select(List.of(
                firstTable,
                secondTable,
                mergedCandidate
        ));

        assertEquals(List.of(firstTable, secondTable), selectedCandidates);
    }

    @Test
    void returnsImmutableSelection() {
        TableCandidate candidate = candidate(area(1, 10, 20, 80, 200), row("A", "B"));

        List<TableCandidate> selectedCandidates = selector.select(List.of(candidate));

        assertThrows(
                UnsupportedOperationException.class,
                () -> selectedCandidates.add(candidate(area(2, 10, 20, 80, 200), row("C", "D")))
        );
    }

    private TableCandidate candidate(
            TableArea area,
            TableRow... rows
    ) {
        return new TableCandidate(area, Arrays.asList(rows));
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
