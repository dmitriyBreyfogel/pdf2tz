package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableNormalizerTest {

    private final TableNormalizer normalizer = new TableNormalizer(new TextCleaner());

    @Test
    void cleansCellsAndRemovesBlankRows() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("watermark"),
                Set.of("example.com")
        );
        TableCandidate candidate = candidate(
                area(1, 10),
                row(" Параметр ", " Значение example.com "),
                row("watermark", "watermark"),
                row(" Скорость ", " 10 мл/ч ")
        );

        List<TableFragment> fragments = normalizer.normalize(List.of(candidate), noiseProfile);

        assertEquals(1, fragments.size());
        assertEquals(2, fragments.get(0).rows().size());
        assertEquals("Параметр", cellText(fragments.get(0), 0, 0));
        assertEquals("Значение", cellText(fragments.get(0), 0, 1));
        assertEquals("Скорость", cellText(fragments.get(0), 1, 0));
        assertEquals("10 мл/ч", cellText(fragments.get(0), 1, 1));
    }

    @Test
    void padsRowsToRectangularShape() {
        TableCandidate candidate = candidate(
                area(1, 10),
                row("Параметр", "Значение", "Комментарий"),
                row("Скорость", "10 мл/ч"),
                row("Объём")
        );

        List<TableFragment> fragments = normalizer.normalize(List.of(candidate), emptyNoiseProfile());
        TableFragment fragment = fragments.get(0);

        assertEquals(3, fragment.columnCount());
        assertEquals("Скорость", cellText(fragment, 1, 0));
        assertEquals("10 мл/ч", cellText(fragment, 1, 1));
        assertEquals("", cellText(fragment, 1, 2));
        assertEquals("Объём", cellText(fragment, 2, 0));
        assertEquals("", cellText(fragment, 2, 1));
        assertEquals("", cellText(fragment, 2, 2));
    }

    @Test
    void skipsCandidateThatBecomesEmptyAfterCleaning() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("watermark"),
                Set.of()
        );
        TableCandidate candidate = candidate(
                area(1, 10),
                row("watermark", "watermark"),
                row("", " ")
        );

        List<TableFragment> fragments = normalizer.normalize(List.of(candidate), noiseProfile);

        assertTrue(fragments.isEmpty());
    }

    @Test
    void returnsFragmentsInReadingOrder() {
        TableCandidate secondPageCandidate = candidate(area(2, 10), row("E", "F"));
        TableCandidate lowerCandidate = candidate(area(1, 100), row("C", "D"));
        TableCandidate upperCandidate = candidate(area(1, 10), row("A", "B"));

        List<TableFragment> fragments = normalizer.normalize(
                List.of(secondPageCandidate, lowerCandidate, upperCandidate),
                emptyNoiseProfile()
        );

        assertEquals(upperCandidate.area(), fragments.get(0).area());
        assertEquals(lowerCandidate.area(), fragments.get(1).area());
        assertEquals(secondPageCandidate.area(), fragments.get(2).area());
    }

    @Test
    void returnsImmutableFragmentList() {
        TableCandidate candidate = candidate(area(1, 10), row("A", "B"));

        List<TableFragment> fragments = normalizer.normalize(List.of(candidate), emptyNoiseProfile());

        assertThrows(
                UnsupportedOperationException.class,
                () -> fragments.add(new TableFragment(area(2, 10), List.of(row("C", "D"))))
        );
    }

    private String cellText(
            TableFragment fragment,
            int rowIndex,
            int cellIndex
    ) {
        return fragment.rows()
                .get(rowIndex)
                .cells()
                .get(cellIndex)
                .text();
    }

    private DocumentNoiseProfile emptyNoiseProfile() {
        return new DocumentNoiseProfile(Set.of(), Set.of());
    }

    private TableCandidate candidate(
            TableArea area,
            TableRow... rows
    ) {
        return new TableCandidate(area, Arrays.asList(rows));
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
