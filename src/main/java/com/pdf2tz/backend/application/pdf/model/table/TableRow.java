package com.pdf2tz.backend.application.pdf.model.table;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Строка таблицы.
 *
 * @param cells ячейки строки
 */
public record TableRow(
        List<TableCell> cells
) {

    public TableRow {
        cells = List.copyOf(Objects.requireNonNull(cells, "Table row cells must not be null"));

        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Table row must contain at least one cell");
        }
    }

    /**
     * Возвращает количество ячеек в строке.
     *
     * @return количество колонок строки
     */
    public int columnCount() {
        return cells.size();
    }

    /**
     * Проверяет, состоит ли строка только из пустых ячеек.
     *
     * @return {@code true}, если все ячейки строки пустые
     */
    public boolean isBlank() {
        return cells.stream().allMatch(TableCell::isBlank);
    }

    /**
     * Возвращает простое текстовое представление строки.
     *
     * <p>Метод пригодится для логирования, диагностики и будущей подготовки
     * таблиц к LLM, но не заменяет структурированное представление ячеек.</p>
     *
     * @return значения ячеек, разделённые символом {@code |}
     */
    public String plainText() {
        return cells.stream()
                .map(TableCell::text)
                .collect(Collectors.joining(" | "));
    }
}
