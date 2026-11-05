package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Выбирает пригодные табличные кандидаты перед нормализацией.
 *
 * <p>Внешний извлекатель таблиц может вернуть слабые кандидаты, дубли одной
 * и той же таблицы или крупные области, которые объединяют несколько разных
 * таблиц. Selector не исправляет содержимое строк и ячеек. Его задача -
 * оставить наиболее правдоподобный набор кандидатов в порядке чтения.</p>
 */
@Component
public class TableCandidateSelector {

    /**
     * Минимальная доля пересечения относительно меньшего кандидата, при которой
     * два кандидата считаются дублями одной таблицы.
     */
    private static final double DUPLICATE_OVERLAP_RATIO = 0.70;

    /**
     * Минимальное количество колонок для кандидата, похожего на таблицу.
     *
     * <p>Одноколоночные результаты чаще являются обычным текстом, который
     * внешний извлекатель ошибочно принял за таблицу.</p>
     */
    private static final int MIN_COLUMN_COUNT = 2;

    /**
     * Минимальное количество заполненных ячеек для сохранения кандидата.
     *
     * <p>Правило отсекает пустые технические кандидаты, но сохраняет компактные
     * таблицы из одной строки, если в них есть несколько значимых ячеек.</p>
     */
    private static final int MIN_FILLED_CELL_COUNT = 2;

    /**
     * Выбирает лучшие табличные кандидаты.
     *
     * <p>Метод возвращает новый список и не меняет входную коллекцию. Результат
     * всегда отсортирован по порядку чтения: страница, верхняя граница, левая
     * граница.</p>
     *
     * @param candidates сырые кандидаты внешнего извлекателя
     * @return выбранные кандидаты без слабых элементов и сильных дублей
     */
    public List<TableCandidate> select(List<TableCandidate> candidates) {
        Objects.requireNonNull(candidates, "Table candidates must not be null");

        List<TableCandidate> meaningfulCandidates = candidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "Table candidate must not be null"))
                .filter(this::isMeaningful)
                .sorted(readingOrder())
                .toList();
        List<TableCandidate> selectedCandidates = new ArrayList<>();

        meaningfulCandidates.stream()
                .filter(candidate -> !overlapsMultipleSeparateCandidates(candidate, meaningfulCandidates))
                .forEach(candidate -> selectCandidate(candidate, selectedCandidates));

        selectedCandidates.sort(readingOrder());

        return List.copyOf(selectedCandidates);
    }

    /**
     * Добавляет кандидата в выбранный набор или заменяет им более слабый дубль.
     *
     * <p>Если кандидат пересекает сразу несколько выбранных элементов, он
     * считается слишком широким объединённым кандидатом и пропускается. Это
     * защищает от ситуации, когда извлекатель склеил несколько соседних таблиц
     * в один прямоугольник.</p>
     */
    private void selectCandidate(
            TableCandidate candidate,
            List<TableCandidate> selectedCandidates
    ) {
        List<TableCandidate> duplicates = selectedCandidates.stream()
                .filter(selectedCandidate -> isDuplicate(candidate, selectedCandidate))
                .toList();

        if (duplicates.isEmpty()) {
            selectedCandidates.add(candidate);
            return;
        }

        if (duplicates.size() > 1) {
            return;
        }

        TableCandidate duplicate = duplicates.get(0);

        if (isBetter(candidate, duplicate)) {
            selectedCandidates.remove(duplicate);
            selectedCandidates.add(candidate);
        }
    }

    private boolean isMeaningful(TableCandidate candidate) {
        return candidate.columnCount() >= MIN_COLUMN_COUNT
                && filledCellCount(candidate) >= MIN_FILLED_CELL_COUNT;
    }

    private boolean isDuplicate(
            TableCandidate candidate,
            TableCandidate selectedCandidate
    ) {
        return candidate.area().overlapRatio(selectedCandidate.area()) >= DUPLICATE_OVERLAP_RATIO;
    }

    /**
     * Проверяет, похож ли кандидат на широкую область, склеившую несколько таблиц.
     *
     * <p>Если кандидат сильно пересекается с несколькими другими кандидатами,
     * которые между собой не являются дублями, он, скорее всего, описывает не
     * одну таблицу, а несколько соседних таблиц сразу. Такой кандидат лучше
     * отбросить до обычного выбора дублей.</p>
     */
    private boolean overlapsMultipleSeparateCandidates(
            TableCandidate candidate,
            List<TableCandidate> allCandidates
    ) {
        List<TableCandidate> overlappingCandidates = allCandidates.stream()
                .filter(otherCandidate -> otherCandidate != candidate)
                .filter(otherCandidate -> isDuplicate(candidate, otherCandidate))
                .toList();

        for (int leftIndex = 0; leftIndex < overlappingCandidates.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < overlappingCandidates.size(); rightIndex++) {
                TableCandidate left = overlappingCandidates.get(leftIndex);
                TableCandidate right = overlappingCandidates.get(rightIndex);

                if (!isDuplicate(left, right)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Сравнивает двух кандидатов, которые уже признаны дублями.
     *
     * <p>Сначала предпочтение отдаётся кандидату с большим количеством
     * непустых строк: это обычно означает, что извлекатель потерял меньше
     * содержимого. Затем учитывается стабильность колонок, количество
     * заполненных ячеек, площадь области и исходный порядок чтения.</p>
     */
    private boolean isBetter(
            TableCandidate candidate,
            TableCandidate current
    ) {
        return qualityComparator().compare(candidate, current) > 0;
    }

    private Comparator<TableCandidate> qualityComparator() {
        return Comparator
                .comparingInt(this::filledRowCount)
                .thenComparing(candidate -> candidate.hasConsistentColumnCount() ? 1 : 0)
                .thenComparingInt(this::filledCellCount)
                .thenComparingDouble(candidate -> candidate.area().areaSize())
                .thenComparing(readingOrder().reversed());
    }

    private Comparator<TableCandidate> readingOrder() {
        return Comparator
                .comparingInt(TableCandidate::pageNumber)
                .thenComparingDouble(candidate -> candidate.area().top())
                .thenComparingDouble(candidate -> candidate.area().left())
                .thenComparingDouble(candidate -> candidate.area().bottom())
                .thenComparingDouble(candidate -> candidate.area().right());
    }

    private int filledCellCount(TableCandidate candidate) {
        return candidate.rows().stream()
                .mapToInt(this::filledCellCount)
                .sum();
    }

    private int filledRowCount(TableCandidate candidate) {
        return (int) candidate.rows().stream()
                .filter(row -> !row.isBlank())
                .count();
    }

    private int filledCellCount(TableRow row) {
        return (int) row.cells().stream()
                .filter(cell -> !cell.isBlank())
                .count();
    }
}
