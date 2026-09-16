package com.pdf2tz.backend.application.pdf.model.table;

import java.util.List;
import java.util.Objects;

/**
 * Сырой табличный кандидат, полученный от внешнего извлекателя таблиц.
 *
 * <p>Кандидат всегда относится к одной странице PDF-документа. На этом уровне
 * данные ещё могут быть грязными: строки могут быть не прямоугольными, внутри
 * ячеек может оставаться служебный шум, а несколько кандидатов могут описывать
 * одну и ту же область страницы. Исправление этих проблем относится к будущей
 * нормализации таблиц, а не к модели кандидата.</p>
 *
 * @param area область расположения на странице
 * @param rows сырые строки таблицы
 */
public record TableCandidate(
        TableArea area,
        List<TableRow> rows
) {

    public TableCandidate {
        Objects.requireNonNull(area, "Table candidate area must not be null");
        rows = List.copyOf(Objects.requireNonNull(rows, "Table candidate rows must not be null"));

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Table candidate must contain at least one row");
        }
    }

    /**
     * Возвращает номер страницы, на которой найден кандидат.
     *
     * @return номер страницы исходного PDF-документа
     */
    public int pageNumber() {
        return area.pageNumber();
    }

    /**
     * Возвращает количество строк кандидата.
     *
     * @return количество строк
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Возвращает максимальное количество колонок среди строк кандидата.
     *
     * <p>Для сырого кандидата это именно максимум, а не строгий контракт:
     * внешний извлекатель может вернуть строки разной длины.</p>
     *
     * @return максимальное количество ячеек в строке
     */
    public int columnCount() {
        return rows.stream()
                .mapToInt(TableRow::columnCount)
                .max()
                .orElse(0);
    }

    /**
     * Проверяет, имеют ли все строки кандидата одинаковое количество ячеек.
     *
     * @return {@code true}, если кандидат уже выглядит прямоугольным
     */
    public boolean hasConsistentColumnCount() {
        int columnCount = columnCount();

        return rows.stream()
                .allMatch(row -> row.columnCount() == columnCount);
    }
}
