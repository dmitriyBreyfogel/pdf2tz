package com.pdf2tz.backend.application.pdf.chunking.table;

import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Консервативно определяет количество строк шапки таблицы.
 *
 * <p>При недостаточной уверенности возвращается {@code 0}: сомнительная
 * строка не должна быть повторена и выдана за context.</p>
 */
@Component
public class TableHeaderDetector {

    private static final int MAX_HEADER_ROWS = 3;
    private static final int MIN_DATA_ROWS_AFTER_HEADER = 2;
    private static final int MIN_LABEL_CELLS = 2;

    /**
     * Определяет header row count в диапазоне от 0 до 3.
     *
     * @param rows строки таблицы
     * @return количество достоверных строк шапки
     */
    public int detect(List<TableRow> rows) {
        if (rows == null || rows.size() < MIN_DATA_ROWS_AFTER_HEADER + 1) {
            return 0;
        }

        int maxCandidate = Math.min(MAX_HEADER_ROWS, rows.size() - MIN_DATA_ROWS_AFTER_HEADER);
        for (int candidate = maxCandidate; candidate >= 1; candidate--) {
            if (looksLikeHeader(rows.subList(0, candidate), rows.subList(candidate, rows.size()))) {
                return candidate;
            }
        }
        return 0;
    }

    private boolean looksLikeHeader(
            List<TableRow> headerRows,
            List<TableRow> dataRows
    ) {
        long labelCells = headerRows.stream()
                .flatMap(row -> row.cells().stream())
                .filter(cell -> !cell.isBlank())
                .filter(this::containsLetters)
                .count();
        if (labelCells < MIN_LABEL_CELLS) {
            return false;
        }

        double headerLetterRatio = letterBearingRatio(headerRows);
        double dataNumericRatio = digitBearingRatio(dataRows);
        return headerLetterRatio >= 0.50
                && (dataNumericRatio >= 0.20 || profileDiffers(headerRows, dataRows));
    }

    private boolean containsLetters(TableCell cell) {
        return cell.text().codePoints().anyMatch(Character::isLetter);
    }

    private double letterBearingRatio(List<TableRow> rows) {
        long total = rows.stream().mapToLong(row -> row.cells().size()).sum();
        long letterBearing = rows.stream()
                .flatMap(row -> row.cells().stream())
                .filter(cell -> !cell.isBlank() && containsLetters(cell))
                .count();
        return total == 0 ? 0 : (double) letterBearing / total;
    }

    private double digitBearingRatio(List<TableRow> rows) {
        long total = rows.stream().mapToLong(row -> row.cells().size()).sum();
        long digitBearing = rows.stream()
                .flatMap(row -> row.cells().stream())
                .filter(cell -> !cell.isBlank())
                .filter(cell -> cell.text().codePoints().anyMatch(Character::isDigit))
                .count();
        return total == 0 ? 0 : (double) digitBearing / total;
    }

    private boolean profileDiffers(List<TableRow> headerRows, List<TableRow> dataRows) {
        double headerAverage = averageCellLength(headerRows);
        double dataAverage = averageCellLength(dataRows);
        return Math.abs(headerAverage - dataAverage) >= 2.0;
    }

    private double averageCellLength(List<TableRow> rows) {
        long count = rows.stream().mapToLong(row -> row.cells().size()).sum();
        long length = rows.stream()
                .flatMap(row -> row.cells().stream())
                .mapToLong(cell -> cell.text().codePointCount(0, cell.text().length()))
                .sum();
        return count == 0 ? 0 : (double) length / count;
    }
}
