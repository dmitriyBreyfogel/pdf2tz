package com.pdf2tz.backend.application.pdf.model.document;

import java.util.Objects;

/**
 * Диапазон страниц исходного PDF, из которого получено содержимое.
 *
 * @param startPageNumber первая страница диапазона
 * @param endPageNumber последняя страница диапазона
 */
public record PageRange(
        int startPageNumber,
        int endPageNumber
) {

    public PageRange {
        if (startPageNumber < 1 || endPageNumber < 1) {
            throw new IllegalArgumentException("Page numbers must be positive");
        }
        if (endPageNumber < startPageNumber) {
            throw new IllegalArgumentException("End page number must not be before start page number");
        }
    }

    public static PageRange single(int pageNumber) {
        return new PageRange(pageNumber, pageNumber);
    }

    public PageRange merge(PageRange other) {
        Objects.requireNonNull(other, "Other page range must not be null");
        return new PageRange(
                Math.min(startPageNumber, other.startPageNumber),
                Math.max(endPageNumber, other.endPageNumber)
        );
    }
}
