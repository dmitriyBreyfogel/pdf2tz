package com.pdf2tz.backend.application.pdf.chunking.table;

import com.pdf2tz.backend.application.pdf.chunking.serialization.TableTextSerializer;
import com.pdf2tz.backend.application.pdf.chunking.text.TextSegmentSplitter;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Делит oversized table только по строкам и сохраняет column provenance.
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
     * @return непустые части в порядке строк
     */
    public List<String> split(ParsedTable table, Predicate<String> fits) {
        Objects.requireNonNull(table, "Parsed table must not be null");
        Objects.requireNonNull(fits, "Fits predicate must not be null");

        String whole = serializer.serialize(table);
        if (fits.test(whole)) {
            return List.of(whole);
        }

        List<TableRow> rows = table.rows();
        int headerRowCount = headerDetector.detect(rows);
        List<TableRow> header = rows.subList(0, headerRowCount);
        List<TableRow> dataRows = rows.subList(headerRowCount, rows.size());

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
            List<TableRow> header,
            List<TableRow> dataRows,
            int assumedPartCount,
            Predicate<String> fits
    ) {
        List<TablePartDraft> drafts = new ArrayList<>();
        List<TableRow> currentRows = new ArrayList<>();

        for (int index = 0; index < dataRows.size(); index++) {
            TableRow row = dataRows.get(index);
            List<TableRow> candidateRows = new ArrayList<>(currentRows);
            candidateRows.add(row);
            int partNumber = drafts.size() + 1;
            List<TableRow> rowsForSerialization = partNumber == 1
                    ? concat(header, candidateRows)
                    : candidateRows;
            String candidate = serializer.serializePart(
                    rowsForSerialization,
                    partNumber,
                    Math.max(assumedPartCount, partNumber),
                    header
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
            String headerOnly = serializer.serializePart(header, 1, 1, List.of());
            if (!fits.test(headerOnly)) {
                throw new IllegalArgumentException("Table header cannot fit into chunk budget");
            }
            drafts.add(TablePartDraft.rows(header));
        }
        return drafts;
    }

    private List<TablePartDraft> oversizedRowDrafts(
            TableRow row,
            int sourceRowNumber,
            int partNumber,
            int assumedPartCount,
            Predicate<String> fits
    ) {
        List<TablePartDraft> result = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < row.cells().size(); cellIndex++) {
            int columnNumber = cellIndex + 1;
            String cellText = row.cells().get(cellIndex).text();
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
                        rawRowPart(sourceRowNumber, columnNumber, cellPart)
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

    private List<String> render(
            List<TablePartDraft> drafts,
            List<TableRow> header,
            int partCount
    ) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            TablePartDraft draft = drafts.get(index);
            if (draft.rawContent() != null) {
                result.add("[TABLE PART " + (index + 1) + '/' + partCount + "]\n"
                        + draft.rawContent()
                        + "\n[TABLE PART]");
                continue;
            }
            List<TableRow> rows = draft.rows();
            List<TableRow> rowsForSerialization = index == 0 ? concat(header, rows) : rows;
            result.add(serializer.serializePart(
                    rowsForSerialization,
                    index + 1,
                    partCount,
                    header
            ));
        }
        return List.copyOf(result);
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
            List<TableRow> rows,
            String rawContent
    ) {
        private static TablePartDraft rows(List<TableRow> rows) {
            return new TablePartDraft(List.copyOf(rows), null);
        }

        private static TablePartDraft raw(String rawContent) {
            return new TablePartDraft(List.of(), rawContent);
        }
    }
}
