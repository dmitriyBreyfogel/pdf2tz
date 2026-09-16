package com.pdf2tz.backend.application.pdf.model.table;

import java.util.Objects;

/**
 * Прямоугольная область таблицы или табличного кандидата на странице PDF-документа.
 *
 * <p>Координаты хранятся в PDF points и должны быть нормализованы адаптером
 * извлечения к единой системе: {@code top/left} - верхний левый угол области,
 * {@code bottom/right} - нижний правый угол. Application-слой использует эту
 * область для сортировки таблиц, поиска пересечений и привязки блока к странице.</p>
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param top верхняя граница области
 * @param left левая граница области
 * @param bottom нижняя граница области
 * @param right правая граница области
 */
public record TableArea(
        int pageNumber,
        double top,
        double left,
        double bottom,
        double right
) {

    public TableArea {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Table area page number must be positive");
        }

        validateCoordinate("top", top);
        validateCoordinate("left", left);
        validateCoordinate("bottom", bottom);
        validateCoordinate("right", right);

        if (bottom <= top) {
            throw new IllegalArgumentException("Table area bottom must be greater than top");
        }

        if (right <= left) {
            throw new IllegalArgumentException("Table area right must be greater than left");
        }
    }

    /**
     * Возвращает ширину области.
     *
     * @return ширина области в PDF points
     */
    public double width() {
        return right - left;
    }

    /**
     * Возвращает высоту области.
     *
     * @return высота области в PDF points
     */
    public double height() {
        return bottom - top;
    }

    /**
     * Возвращает площадь области.
     *
     * @return площадь области в квадратных PDF points
     */
    public double areaSize() {
        return width() * height();
    }

    /**
     * Проверяет, пересекается ли текущая область с другой областью на той же странице.
     *
     * @param other другая область таблицы
     * @return {@code true}, если области находятся на одной странице и имеют общее пространство
     */
    public boolean intersects(TableArea other) {
        Objects.requireNonNull(other, "Other table area must not be null");

        return pageNumber == other.pageNumber
                && left < other.right
                && right > other.left
                && top < other.bottom
                && bottom > other.top;
    }

    /**
     * Рассчитывает площадь пересечения двух областей.
     *
     * @param other другая область таблицы
     * @return площадь пересечения или {@code 0}, если пересечения нет
     */
    public double intersectionArea(TableArea other) {
        if (!intersects(other)) {
            return 0;
        }

        double intersectionWidth = Math.min(right, other.right) - Math.max(left, other.left);
        double intersectionHeight = Math.min(bottom, other.bottom) - Math.max(top, other.top);

        return intersectionWidth * intersectionHeight;
    }

    /**
     * Рассчитывает долю пересечения относительно меньшей из двух областей.
     *
     * <p>Такой показатель удобен для будущего выбора между пересекающимися
     * кандидатами: если значение близко к {@code 1}, кандидаты почти полностью
     * описывают один и тот же участок страницы.</p>
     *
     * @param other другая область таблицы
     * @return значение от {@code 0} до {@code 1}
     */
    public double overlapRatio(TableArea other) {
        double smallerArea = Math.min(
                areaSize(),
                Objects.requireNonNull(other, "Other table area must not be null").areaSize()
        );

        return intersectionArea(other) / smallerArea;
    }

    private void validateCoordinate(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Table area " + name + " coordinate must be finite and non-negative");
        }
    }
}
