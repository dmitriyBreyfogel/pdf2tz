package com.pdf2tz.backend.application.pdf.chunking.table;

import com.pdf2tz.backend.application.pdf.chunking.serialization.TableTextSerializer;
import com.pdf2tz.backend.application.pdf.chunking.text.TextSegmentSplitter;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable.SourcedRow;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Делит большую таблицу по строкам, а слишком длинную строку по ячейкам.
 * Для каждой части сохраняет страницы исходных строк и номера колонок.
 */
@Component
public class TableChunkSplitter {

    private final TableTextSerializer serializer;
    private final TableHeaderDetector headerDetector;
    private final TextSegmentSplitter textSegmentSplitter;
    private final LlmTokenizerPort tokenizer;

    public TableChunkSplitter(
            TableTextSerializer serializer,
            TableHeaderDetector headerDetector,
            TextSegmentSplitter textSegmentSplitter,
            LlmTokenizerPort tokenizer
    ) {
        this.serializer = Objects.requireNonNull(serializer, "Table serializer must not be null");
        this.headerDetector = Objects.requireNonNull(headerDetector, "Table header detector must not be null");
        this.textSegmentSplitter = Objects.requireNonNull(
                textSegmentSplitter,
                "Text segment splitter must not be null"
        );
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer must not be null");
    }

    /**
     * Делит таблицу на payload parts, каждый из которых удовлетворяет fits.
     *
     * @param table таблица
     * @param fits проверка полного chunk content для body part
     * @return непустые части с диапазонами исходных страниц в порядке строк
     */
    public List<TableChunkPart> split(ParsedTable table, Predicate<String> fits) {
        Objects.requireNonNull(table, "Parsed table must not be null");
        Objects.requireNonNull(fits, "Fits predicate must not be null");

        String whole = serializer.serialize(table);
        if (fits.test(whole)) {
            return List.of(new TableChunkPart(whole,
                    sourcePages(table.rowsWithSourcePages())));
        }

        List<SourcedRow> rows = table.rowsWithSourcePages();
        int headerRowCount = headerDetector.detect(tableRows(rows));
        List<SourcedRow> header = rows.subList(0, headerRowCount);
        List<SourcedRow> dataRows = rows.subList(headerRowCount, rows.size());
        if (!header.isEmpty()
                && !fits.test(serializer.serializePart(tableRows(header), 1, 1, List.of()))) {
            // Слишком длинная шапка остаётся обычной строкой: её также нужно
            // сохранить через row/cell fallback, а не потерять при делении.
            header = List.of();
            dataRows = rows;
        }

        int assumedPartCount = 1;
        List<TablePartDraft> drafts = List.of();
        for (int iteration = 0; iteration < 8; iteration++) {
            drafts = buildDrafts(header, dataRows, assumedPartCount, fits);
            int actualCount = drafts.size();
            if (actualCount == assumedPartCount) {
                return render(drafts, header, actualCount);
            }
            assumedPartCount = Math.max(1, actualCount);
        }
        return render(drafts, header, drafts.size());
    }

    private List<TablePartDraft> buildDrafts(
            List<SourcedRow> header,
            List<SourcedRow> dataRows,
            int assumedPartCount,
            Predicate<String> fits
    ) {
        List<TablePartDraft> drafts = new ArrayList<>();
        List<SourcedRow> currentRows = new ArrayList<>();

        for (int index = 0; index < dataRows.size(); index++) {
            SourcedRow row = dataRows.get(index);
            List<SourcedRow> candidateRows = new ArrayList<>(currentRows);
            candidateRows.add(row);
            int partNumber = drafts.size() + 1;
            List<TableRow> rowsForSerialization = partNumber == 1
                    ? concat(tableRows(header), tableRows(candidateRows))
                    : tableRows(candidateRows);
            String candidate = serializer.serializePart(
                    rowsForSerialization,
                    partNumber,
                    Math.max(assumedPartCount, partNumber),
                    tableRows(header)
            );

            if (fits.test(candidate)) {
                currentRows = candidateRows;
                continue;
            }

            if (!currentRows.isEmpty()) {
                drafts.add(TablePartDraft.rows(currentRows));
                currentRows = new ArrayList<>();
                index--;
                continue;
            }

            if (drafts.isEmpty() && !header.isEmpty()) {
                // Если шапка и первая строка не помещаются вместе, шапка получает
                // собственную часть, а строка повторно проверяется без неё.
                drafts.add(TablePartDraft.headerOnly(sourcePages(header)));
                index--;
                continue;
            }

            drafts.addAll(oversizedRowDrafts(
                    row,
                    index + header.size() + 1,
                    partNumber,
                    assumedPartCount,
                    fits
            ));
        }

        if (!currentRows.isEmpty()) {
            drafts.add(TablePartDraft.rows(currentRows));
        }

        if (drafts.isEmpty() && !header.isEmpty()) {
            String headerOnly = serializer.serializePart(tableRows(header), 1, 1, List.of());
            if (!fits.test(headerOnly)) {
                throw new IllegalArgumentException("Table header cannot fit into chunk budget");
            }
            drafts.add(TablePartDraft.headerOnly(sourcePages(header)));
        }
        return drafts;
    }

    private List<TablePartDraft> oversizedRowDrafts(
            SourcedRow row,
            int sourceRowNumber,
            int partNumber,
            int assumedPartCount,
            Predicate<String> fits
    ) {
        List<TablePartDraft> result = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < row.row().cells().size(); cellIndex++) {
            int columnNumber = cellIndex + 1;
            String cellText = row.row().cells().get(cellIndex).text();
            List<String> cellParts = splitCell(
                    cellText,
                    candidate -> fits.test(rawTablePart(
                            sourceRowNumber,
                            columnNumber,
                            candidate,
                            partNumber,
                            Math.max(assumedPartCount, partNumber)
                    ))
            );
            for (String cellPart : cellParts) {
                result.add(TablePartDraft.raw(
                        rawRowPart(sourceRowNumber, columnNumber, cellPart),
                        row.pageNumber()
                ));
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Table row cannot fit into chunk budget");
        }
        return result;
    }

    private List<String> splitCell(
            String cellText,
            Predicate<String> fits
    ) {
        List<String> result = new ArrayList<>();
        if (cellText.isBlank()) {
            return List.of("");
        }
        String remainder = cellText;
        while (!remainder.isBlank()) {
            String current = remainder;
            var split = textSegmentSplitter.splitFirst(
                    current,
                    fits
            );
            if (split.acceptedPrefix().isBlank()) {
                if (tokenizer.countTokens(remainder) <= 1) {
                    throw new IllegalArgumentException("Table cell cannot fit into chunk budget");
                }
                List<String> tokenParts = tokenizer.splitByTokenLimit(remainder, 1);
                result.addAll(tokenParts);
                break;
            }
            result.add(split.acceptedPrefix());
            remainder = split.remainder();
        }
        return result;
    }

    private List<TableChunkPart> render(
            List<TablePartDraft> drafts,
            List<SourcedRow> header,
            int partCount
    ) {
        List<TableChunkPart> result = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            TablePartDraft draft = drafts.get(index);
            if (draft.rawContent() != null) {
                result.add(new TableChunkPart("[TABLE PART " + (index + 1) + '/' + partCount + "]\n"
                        + draft.rawContent()
                        + "\n[TABLE PART]", draft.sourcePages()));
                continue;
            }
            List<TableRow> rows = tableRows(draft.rows());
            List<TableRow> rowsForSerialization = index == 0
                    ? concat(tableRows(header), rows) : rows;
            PageRange pages = index == 0 && !header.isEmpty()
                    ? draft.sourcePages().merge(sourcePages(header))
                    : draft.sourcePages();
            result.add(new TableChunkPart(serializer.serializePart(
                    rowsForSerialization,
                    index + 1,
                    partCount,
                    tableRows(header)
            ), pages));
        }
        return List.copyOf(result);
    }

    private static List<TableRow> tableRows(List<SourcedRow> rows) {
        return rows.stream().map(SourcedRow::row).toList();
    }

    private static PageRange sourcePages(List<SourcedRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Table part must contain source rows");
        }
        return new PageRange(rows.get(0).pageNumber(),
                rows.get(rows.size() - 1).pageNumber());
    }

    private String columnPayload(int columnNumber, String text) {
        return "[COLUMN " + columnNumber + "]\n"
                + text
                + "\n[/COLUMN]";
    }

    private String rawRowPart(int sourceRowNumber, int columnNumber, String text) {
        return "[TABLE ROW " + sourceRowNumber + " PART]\n"
                + columnPayload(columnNumber, text)
                + "\n[/TABLE ROW PART]";
    }

    private String rawTablePart(
            int sourceRowNumber,
            int columnNumber,
            String text,
            int partNumber,
            int partCount
    ) {
        return "[TABLE PART " + partNumber + '/' + partCount + "]\n"
                + rawRowPart(sourceRowNumber, columnNumber, text)
                + "\n[TABLE PART]";
    }

    private List<TableRow> concat(List<TableRow> first, List<TableRow> second) {
        List<TableRow> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private record TablePartDraft(
            List<SourcedRow> rows,
            String rawContent,
            PageRange sourcePages
    ) {
        private static TablePartDraft rows(List<SourcedRow> rows) {
            return new TablePartDraft(List.copyOf(rows), null,
                    TableChunkSplitter.sourcePages(rows));
        }

        private static TablePartDraft headerOnly(PageRange pages) {
            return new TablePartDraft(List.of(), null, pages);
        }

        private static TablePartDraft raw(String rawContent, int pageNumber) {
            return new TablePartDraft(List.of(), rawContent, PageRange.single(pageNumber));
        }
    }
}
