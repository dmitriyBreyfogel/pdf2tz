package com.pdf2tz.backend.application.pdf.model.table;

import java.util.List;
import java.util.Objects;

/**
 * Целостная таблица документа.
 *
 * <p>Может состоять из одного фрагмента или из нескольких фрагментов,
 * если таблица продолжается на следующих страницах.</p>
 *
 * @param fragments фрагменты таблицы в порядке чтения
 */
public record ParsedTable(
        List<TableFragment> fragments
) {

    public ParsedTable {
        fragments = List.copyOf(Objects.requireNonNull(fragments, "Parsed table fragments must not be null"));

        if (fragments.isEmpty()) {
            throw new IllegalArgumentException("Parsed table must contain at least one fragment");
        }

        validateFragmentOrder(fragments);
        validateColumnCount(fragments);
    }

    /**
     * Возвращает строки всех фрагментов таблицы в порядке чтения.
     *
     * @return строки целостной таблицы
     */
    public List<TableRow> rows() {
        return fragments.stream()
                .flatMap(fragment -> fragment.rows().stream())
                .toList();
    }

    /**
     * Возвращает номер страницы, на которой начинается таблица.
     *
     * @return номер первой страницы таблицы
     */
    public int startPageNumber() {
        return fragments.get(0).area().pageNumber();
    }

    /**
     * Возвращает номер страницы, на которой заканчивается таблица.
     *
     * @return номер последней страницы таблицы
     */
    public int endPageNumber() {
        return fragments.get(fragments.size() - 1).area().pageNumber();
    }

    /**
     * Возвращает ожидаемое количество колонок таблицы.
     *
     * @return количество колонок первого фрагмента таблицы
     */
    public int columnCount() {
        return fragments.get(0).columnCount();
    }

    /**
     * Проверяет, переносилась ли таблица между несколькими страницами.
     *
     * @return {@code true}, если таблица состоит из фрагментов на разных страницах
     */
    public boolean isMultiPage() {
        return startPageNumber() != endPageNumber();
    }

    private void validateFragmentOrder(List<TableFragment> fragments) {
        for (int index = 1; index < fragments.size(); index++) {
            TableFragment previous = fragments.get(index - 1);
            TableFragment current = fragments.get(index);

            if (isBefore(current, previous)) {
                throw new IllegalArgumentException("Parsed table fragments must be ordered by reading order");
            }
        }
    }

    private void validateColumnCount(List<TableFragment> fragments) {
        int columnCount = fragments.get(0).columnCount();

        boolean hasDifferentColumnCount = fragments.stream()
                .anyMatch(fragment -> fragment.columnCount() != columnCount);

        if (hasDifferentColumnCount) {
            throw new IllegalArgumentException("Parsed table fragments must have the same column count");
        }
    }

    private boolean isBefore(
            TableFragment current,
            TableFragment previous
    ) {
        int pageCompare = Integer.compare(current.pageNumber(), previous.pageNumber());

        if (pageCompare != 0) {
            return pageCompare < 0;
        }

        int topCompare = Double.compare(current.area().top(), previous.area().top());

        if (topCompare != 0) {
            return topCompare < 0;
        }

        return Double.compare(current.area().left(), previous.area().left()) < 0;
    }
}
