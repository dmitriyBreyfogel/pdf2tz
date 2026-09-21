package com.pdf2tz.backend.application.pdf.chunking.serialization;

import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сериализует структурированную таблицу в lossless tagged text.
 *
 * <p>Формат не объявляет первую строку header-ом автоматически: это решение
 * оставлено {@code TableHeaderDetector} для oversized-таблиц.</p>
 */
@Component
public class TableTextSerializer {

    /**
     * Сериализует целую таблицу.
     *
     * @param table таблица
     * @return tagged table payload
     */
    public String serialize(ParsedTable table) {
        Objects.requireNonNull(table, "Parsed table must not be null");
        return serializeRows(table.rows());
    }

    /**
     * Сериализует заданные строки как одну таблицу без изменения их порядка.
     *
     * @param rows строки таблицы
     * @return tagged table payload
     */
    public String serializeRows(List<TableRow> rows) {
        Objects.requireNonNull(rows, "Table rows must not be null");
        String tableRows = rows.stream()
                .map(this::serializeRow)
                .collect(Collectors.joining("\n"));
        return "[TABLE]\n" + tableRows + "\n[/TABLE]";
    }

    /**
     * Сериализует часть большой таблицы с явной нумерацией.
     *
     * @param rows строки части
     * @param partNumber номер части
     * @param partCount общее число частей
     * @param repeatedHeader уже повторённая context-шапка
     * @return tagged table part payload
     */
    public String serializePart(
            List<TableRow> rows,
            int partNumber,
            int partCount,
            List<TableRow> repeatedHeader
    ) {
        if (partNumber < 1 || partCount < partNumber) {
            throw new IllegalArgumentException("Invalid table part numbers");
        }
        Objects.requireNonNull(repeatedHeader, "Repeated header must not be null");

        StringBuilder result = new StringBuilder()
                .append("[TABLE PART ")
                .append(partNumber)
                .append('/')
                .append(partCount)
                .append("]\n");
        if (!repeatedHeader.isEmpty() && partNumber > 1) {
            result.append("[REPEATED HEADER]\n")
                    .append(repeatedHeader.stream().map(this::serializeRow).collect(Collectors.joining("\n")))
                    .append("\n[/REPEATED HEADER]\n");
        }
        result.append(rows.stream().map(this::serializeRow).collect(Collectors.joining("\n")))
                .append("\n[TABLE PART]");
        return result.toString();
    }

    String serializeRow(TableRow row) {
        Objects.requireNonNull(row, "Table row must not be null");
        return row.cells().stream()
                .map(TableCell::text)
                .map(this::escapeCell)
                .collect(Collectors.joining(" | ", "| ", " |"));
    }

    private String escapeCell(String value) {
        return Objects.requireNonNull(value, "Table cell text must not be null")
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "<br>");
    }
}
