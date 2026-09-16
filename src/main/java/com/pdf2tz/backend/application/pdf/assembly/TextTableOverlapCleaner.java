package com.pdf2tz.backend.application.pdf.assembly;

import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextPage;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Удаляет из очищенного текстового слоя строки, которые уже представлены структурированными таблицами.
 *
 * <p>Tika читает таблицу как обычные текстовые строки, а Tabula дополнительно
 * может извлечь ту же таблицу в структурированном виде. Если оставить оба варианта,
 * будущие чанки получат дубли: один раз таблицу как текст, второй раз как {@link com.pdf2tz.backend.application.pdf.model.document.TableBlock}.
 * Этот компонент отвечает только за аккуратное удаление таких дублей из текста страницы.</p>
 *
 * <p>Очистка намеренно консервативная: строка удаляется только при уверенном текстовом
 * совпадении со строкой таблицы на той же физической странице. Без координат текстового
 * слоя безопаснее оставить сомнительный дубль, чем удалить полезный абзац.</p>
 */
@Component
public class TextTableOverlapCleaner {

    private static final String LINE_SEPARATOR = "\n";

    /**
     * Минимальная доля строки таблицы в строке текста, при которой совпадение считается уверенным.
     */
    private static final double MIN_ROW_COVERAGE_RATIO = 0.65;

    /**
     * Минимальная доля одноколоночной строки таблицы в строке текста.
     *
     * <p>Для одной ячейки критерий строже: обычный текстовый абзац чаще может
     * содержать похожий фрагмент, поэтому удаляем только почти полные совпадения.</p>
     */
    private static final double MIN_SINGLE_CELL_ROW_COVERAGE_RATIO = 0.90;

    /**
     * Минимальная длина содержательной ячейки.
     */
    private static final int MIN_STRONG_CELL_LENGTH = 3;

    /**
     * Минимальная длина одноколоночной строки, которую можно удалять из текста.
     */
    private static final int MIN_SINGLE_CELL_ROW_LENGTH = 20;

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Удаляет из текста страницы строки, совпадающие со строками таблиц этой же страницы.
     *
     * @param page очищенная страница текстового слоя
     * @param tables структурированные таблицы документа
     * @return текст страницы без уверенно распознанных табличных дублей
     */
    public String removeOverlaps(
            CleanedTextPage page,
            List<ParsedTable> tables
    ) {
        Objects.requireNonNull(page, "Cleaned text page must not be null");
        Objects.requireNonNull(tables, "Parsed tables must not be null");

        List<TableRowSignature> rowSignatures = rowSignatures(page.pageNumber(), tables);

        if (rowSignatures.isEmpty()) {
            return Objects.requireNonNull(page.text(), "Cleaned text page text must not be null")
                    .trim();
        }

        return page.text()
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !isTableRowDuplicate(line, rowSignatures))
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private boolean isTableRowDuplicate(
            String line,
            List<TableRowSignature> rowSignatures
    ) {
        String lineCompact = compact(line);

        if (lineCompact.isBlank()) {
            return false;
        }

        return rowSignatures.stream()
                .anyMatch(signature -> signature.matches(lineCompact));
    }

    private List<TableRowSignature> rowSignatures(
            int pageNumber,
            List<ParsedTable> tables
    ) {
        return tables.stream()
                .map(table -> Objects.requireNonNull(table, "Parsed table must not be null"))
                .flatMap(table -> table.fragments().stream())
                .filter(fragment -> fragment.pageNumber() == pageNumber)
                .flatMap(fragment -> rowSignatures(fragment).stream())
                .toList();
    }

    private List<TableRowSignature> rowSignatures(TableFragment fragment) {
        return fragment.rows().stream()
                .map(TableRowSignature::from)
                .filter(TableRowSignature::isUsable)
                .toList();
    }

    private static String compact(String text) {
        String normalizedText = Objects.requireNonNull(text, "Text must not be null")
                .replace('ё', 'е')
                .toLowerCase(Locale.ROOT);

        return NON_ALPHANUMERIC_PATTERN.matcher(normalizedText)
                .replaceAll("");
    }

    private record TableRowSignature(
            List<String> cellCompacts,
            String rowCompact,
            long strongCellCount
    ) {

        private static TableRowSignature from(TableRow row) {
            List<String> cellCompacts = row.cells().stream()
                    .map(TableCell::text)
                    .map(TextTableOverlapCleaner::compact)
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .toList();
            String rowCompact = String.join("", cellCompacts);
            long strongCellCount = cellCompacts.stream()
                    .filter(cell -> cell.length() >= MIN_STRONG_CELL_LENGTH)
                    .count();

            return new TableRowSignature(
                    cellCompacts,
                    rowCompact,
                    strongCellCount
            );
        }

        private boolean isUsable() {
            return !rowCompact.isBlank()
                    && (cellCompacts.size() >= 2 || rowCompact.length() >= MIN_SINGLE_CELL_ROW_LENGTH)
                    && strongCellCount > 0;
        }

        private boolean matches(String lineCompact) {
            if (lineCompact.equals(rowCompact)) {
                return true;
            }

            if (cellCompacts.size() == 1) {
                return lineCompact.contains(rowCompact)
                        && coverageRatio(lineCompact) >= MIN_SINGLE_CELL_ROW_COVERAGE_RATIO;
            }

            return containsAllCells(lineCompact)
                    && coverageRatio(lineCompact) >= MIN_ROW_COVERAGE_RATIO;
        }

        private boolean containsAllCells(String lineCompact) {
            return cellCompacts.stream().allMatch(lineCompact::contains);
        }

        private double coverageRatio(String lineCompact) {
            return (double) rowCompact.length() / lineCompact.length();
        }
    }
}
