package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Приводит выбранные табличные кандидаты к нормализованным фрагментам таблиц.
 *
 * <p>Normalizer работает после {@link TableCandidateSelector}: на вход должны
 * приходить уже выбранные кандидаты без явных дублей. Этот класс не объединяет
 * таблицы между страницами и не пытается восстановить сложные переносы строк.
 * Его ответственность - очистить ячейки, удалить пустые строки и привести
 * каждую таблицу к прямоугольному виду, пригодному для {@link TableFragment}.</p>
 */
@Component
public class TableNormalizer {

    private final TextCleaner textCleaner;

    public TableNormalizer(TextCleaner textCleaner) {
        this.textCleaner = Objects.requireNonNull(textCleaner, "Text cleaner must not be null");
    }

    /**
     * Нормализует выбранные табличные кандидаты.
     *
     * <p>Кандидаты, которые после очистки не содержат ни одной непустой строки,
     * отбрасываются. Результат возвращается в порядке чтения.</p>
     *
     * @param candidates выбранные сырые кандидаты таблиц
     * @param noiseProfile профиль служебного шума документа
     * @return нормализованные фрагменты таблиц
     */
    public List<TableFragment> normalize(
            List<TableCandidate> candidates,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(candidates, "Table candidates must not be null");
        Objects.requireNonNull(noiseProfile, "Noise profile must not be null");

        return candidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "Table candidate must not be null"))
                .map(candidate -> normalizeCandidate(candidate, noiseProfile))
                .flatMap(Optional::stream)
                .sorted(readingOrder())
                .toList();
    }

    private Optional<TableFragment> normalizeCandidate(
            TableCandidate candidate,
            DocumentNoiseProfile noiseProfile
    ) {
        List<TableRow> cleanedRows = candidate.rows().stream()
                .map(row -> cleanRow(row, noiseProfile))
                .filter(row -> !row.isBlank())
                .toList();

        if (cleanedRows.isEmpty()) {
            return Optional.empty();
        }

        int columnCount = maxColumnCount(cleanedRows);
        List<TableRow> normalizedRows = cleanedRows.stream()
                .map(row -> padRow(row, columnCount))
                .toList();

        return Optional.of(new TableFragment(
                candidate.area(),
                normalizedRows
        ));
    }

    private TableRow cleanRow(
            TableRow row,
            DocumentNoiseProfile noiseProfile
    ) {
        List<TableCell> cleanedCells = row.cells().stream()
                .map(cell -> new TableCell(textCleaner.cleanTextFragment(
                        cell.text(),
                        noiseProfile
                )))
                .toList();

        return new TableRow(cleanedCells);
    }

    private TableRow padRow(
            TableRow row,
            int columnCount
    ) {
        if (row.columnCount() == columnCount) {
            return row;
        }

        List<TableCell> cells = new ArrayList<>(row.cells());

        while (cells.size() < columnCount) {
            cells.add(new TableCell(""));
        }

        return new TableRow(cells);
    }

    private int maxColumnCount(List<TableRow> rows) {
        return rows.stream()
                .mapToInt(TableRow::columnCount)
                .max()
                .orElseThrow();
    }

    private Comparator<TableFragment> readingOrder() {
        return Comparator
                .comparingInt(TableFragment::pageNumber)
                .thenComparingDouble(fragment -> fragment.area().top())
                .thenComparingDouble(fragment -> fragment.area().left())
                .thenComparingDouble(fragment -> fragment.area().bottom())
                .thenComparingDouble(fragment -> fragment.area().right());
    }
}
