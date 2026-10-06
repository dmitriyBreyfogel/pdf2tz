package com.pdf2tz.backend.application.pdf.model.table;

import java.util.ArrayList;
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
     * Возвращает строки таблицы без повторной первой строки-шапки на границах фрагментов.
     *
     * <p>Пропускается только точное повторение текстовой шапки в начале продолжения.
     * Повторные строки данных внутри таблицы сохраняются. Исходные строки фрагментов
     * не меняются: они нужны для поиска дублей на каждой физической странице.</p>
     *
     * @return строки целостной таблицы
     */
    public List<TableRow> rows() {
        return rowsWithSourcePages().stream()
                .map(SourcedRow::row)
                .toList();
    }

    /**
     * Возвращает те же логические строки вместе со страницей их исходного фрагмента.
     * Повторная шапка продолжения пропускается по тому же правилу, что и в {@link #rows()}.
     * Список вычисляется из фрагментов и не хранит вторую копию таблицы.
     *
     * @return строки с номерами физических страниц
     */
    public List<SourcedRow> rowsWithSourcePages() {
        TableRow header = fragments.get(0).rows().get(0);
        boolean hasTextLabels = header.cells().stream()
                .filter(cell -> cell.text().codePoints().filter(Character::isLetter).count() >= 3)
                .count() >= 2;
        List<SourcedRow> result = new ArrayList<>();
        for (int index = 0; index < fragments.size(); index++) {
            TableFragment fragment = fragments.get(index);
            List<TableRow> rows = fragment.rows();
            int firstRow = index > 0 && hasTextLabels && header.equals(rows.get(0)) ? 1 : 0;
            for (int rowIndex = firstRow; rowIndex < rows.size(); rowIndex++) {
                result.add(new SourcedRow(rows.get(rowIndex), fragment.pageNumber()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Строка принятой таблицы и страница её исходного фрагмента.
     *
     * @param row логическая строка
     * @param pageNumber физическая страница фрагмента
     */
    public record SourcedRow(TableRow row, int pageNumber) {
        public SourcedRow {
            Objects.requireNonNull(row, "Table row must not be null");
            if (pageNumber < 1) {
                throw new IllegalArgumentException("Source page number must be positive");
            }
        }
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
