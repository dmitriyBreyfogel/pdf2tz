package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Собирает целостные таблицы из нормализованных табличных фрагментов.
 *
 * <p>Assembler работает после {@link TableNormalizer}: на вход должны приходить
 * прямоугольные очищенные фрагменты таблиц. Сервис не меняет строки и ячейки,
 * а только решает, является ли следующий фрагмент продолжением предыдущей
 * логической таблицы.</p>
 */
@Component
public class TableAssembler {

    /**
     * Максимальное расстояние между страницами для фрагментов одной таблицы.
     *
     * <p>Первая версия поддерживает только прямое продолжение на следующей
     * странице. Если между фрагментами есть пропущенная страница, они считаются
     * разными таблицами.</p>
     */
    private static final int CONTINUATION_PAGE_DISTANCE = 1;

    /**
     * Абсолютный допуск для сравнения горизонтальных границ фрагментов.
     */
    private static final double HORIZONTAL_BOUNDARY_TOLERANCE_POINTS = 12.0;

    /**
     * Относительный допуск для сравнения горизонтальных границ фрагментов.
     *
     * <p>Используется вместе с абсолютным допуском, чтобы одинаково нормально
     * работать с узкими и широкими таблицами.</p>
     */
    private static final double HORIZONTAL_BOUNDARY_TOLERANCE_RATIO = 0.05;

    /**
     * Минимальная нижняя граница предыдущего фрагмента для проверки продолжения.
     *
     * <p>Пока модель не хранит высоту страницы, используется осторожная
     * эвристика в PDF points: фрагмент должен находиться достаточно низко,
     * чтобы быть похожим на таблицу, оборванную концом страницы.</p>
     */
    private static final double MIN_PREVIOUS_FRAGMENT_BOTTOM_FOR_CONTINUATION = 500.0;

    /**
     * Максимальная верхняя граница текущего фрагмента для проверки продолжения.
     *
     * <p>Продолжение таблицы обычно начинается близко к верхней части следующей
     * страницы. Если фрагмент начинается заметно ниже, безопаснее считать его
     * отдельной таблицей.</p>
     */
    private static final double MAX_CURRENT_FRAGMENT_TOP_FOR_CONTINUATION = 180.0;

    /**
     * Собирает нормализованные фрагменты в целостные таблицы.
     *
     * <p>Входной список может быть в произвольном порядке. Результат всегда
     * отсортирован по порядку чтения первой страницы каждой таблицы.</p>
     *
     * @param fragments нормализованные фрагменты таблиц
     * @return целостные таблицы документа
     */
    public List<ParsedTable> assemble(List<TableFragment> fragments) {
        Objects.requireNonNull(fragments, "Table fragments must not be null");

        List<TableFragment> sortedFragments = fragments.stream()
                .map(fragment -> Objects.requireNonNull(fragment, "Table fragment must not be null"))
                .sorted(readingOrder())
                .toList();

        if (sortedFragments.isEmpty()) {
            return List.of();
        }

        List<ParsedTable> tables = new ArrayList<>();
        List<TableFragment> currentTableFragments = new ArrayList<>();

        for (TableFragment fragment : sortedFragments) {
            if (currentTableFragments.isEmpty()) {
                currentTableFragments.add(fragment);
                continue;
            }

            TableFragment previousFragment = currentTableFragments.get(currentTableFragments.size() - 1);

            if (isContinuation(previousFragment, fragment)) {
                currentTableFragments.add(fragment);
            }
            else {
                tables.add(new ParsedTable(currentTableFragments));
                currentTableFragments = new ArrayList<>();
                currentTableFragments.add(fragment);
            }
        }

        tables.add(new ParsedTable(currentTableFragments));

        return List.copyOf(tables);
    }

    /**
     * Проверяет, является ли текущий фрагмент продолжением предыдущего.
     *
     * <p>Эвристика намеренно строгая: фрагменты должны находиться на соседних
     * страницах, иметь одинаковое количество колонок, близкие горизонтальные
     * границы и выглядеть как разрыв на границе страниц. Такой подход может не
     * склеить часть реальных продолжений, зато снижает риск ошибочно объединить
     * разные таблицы.</p>
     */
    private boolean isContinuation(
            TableFragment previous,
            TableFragment current
    ) {
        return current.pageNumber() - previous.pageNumber() == CONTINUATION_PAGE_DISTANCE
                && current.columnCount() == previous.columnCount()
                && hasAlignedHorizontalBounds(previous, current)
                && isNearPageBreak(previous, current);
    }

    private boolean hasAlignedHorizontalBounds(
            TableFragment previous,
            TableFragment current
    ) {
        double tolerance = horizontalTolerance(previous, current);

        return Math.abs(previous.area().left() - current.area().left()) <= tolerance
                && Math.abs(previous.area().right() - current.area().right()) <= tolerance;
    }

    private double horizontalTolerance(
            TableFragment previous,
            TableFragment current
    ) {
        double maxWidth = Math.max(
                previous.area().width(),
                current.area().width()
        );

        return Math.max(
                HORIZONTAL_BOUNDARY_TOLERANCE_POINTS,
                maxWidth * HORIZONTAL_BOUNDARY_TOLERANCE_RATIO
        );
    }

    private boolean isNearPageBreak(
            TableFragment previous,
            TableFragment current
    ) {
        return previous.area().bottom() >= MIN_PREVIOUS_FRAGMENT_BOTTOM_FOR_CONTINUATION
                && current.area().top() <= MAX_CURRENT_FRAGMENT_TOP_FOR_CONTINUATION;
    }

    private Comparator<TableFragment> readingOrder() {
        return Comparator
                .comparingInt(TableFragment::pageNumber)
                .thenComparingDouble(fragment -> fragment.area().top())
                .thenComparingDouble(fragment -> fragment.area().left())
                .thenComparingDouble(fragment -> fragment.area().bottom())
                .thenComparingDouble(fragment -> fragment.area().right());
    }
}
