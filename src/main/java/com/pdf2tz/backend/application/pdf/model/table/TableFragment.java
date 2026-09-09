package com.pdf2tz.backend.application.pdf.model.table;

import java.util.List;
import java.util.Objects;

/**
 * Нормализованный фрагмент таблицы на одной странице документа.
 *
 * <p>Фрагмент уже очищен от служебного текста и прошёл разрешение
 * пересечений с другими табличными кандидатами. В отличие от
 * {@link TableCandidate}, фрагмент должен быть прямоугольным: каждая строка
 * содержит одинаковое количество ячеек.</p>
 *
 * @param area область расположения на странице
 * @param rows нормализованные строки
 */
public record TableFragment(
        TableArea area,
        List<TableRow> rows
) {

    public TableFragment {
        Objects.requireNonNull(area, "Table fragment area must not be null");
        rows = List.copyOf(Objects.requireNonNull(rows, "Table fragment rows must not be null"));

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Table fragment must contain at least one row");
        }

        validateRowsShape(rows);
    }

    /**
     * Возвращает номер страницы, на которой расположен фрагмент.
     *
     * @return номер страницы исходного PDF-документа
     */
    public int pageNumber() {
        return area.pageNumber();
    }

    /**
     * Возвращает количество строк фрагмента.
     *
     * @return количество строк
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Возвращает количество колонок фрагмента.
     *
     * @return количество ячеек в каждой строке
     */
    public int columnCount() {
        return rows.get(0).columnCount();
    }

    private void validateRowsShape(List<TableRow> rows) {
        int columnCount = rows.get(0).columnCount();

        boolean hasDifferentColumnCount = rows.stream()
                .anyMatch(row -> row.columnCount() != columnCount);

        if (hasDifferentColumnCount) {
            throw new IllegalArgumentException("Table fragment rows must have the same column count");
        }
    }
}
