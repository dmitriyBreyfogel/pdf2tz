package com.pdf2tz.backend.application.pdf.chunking.structure;

/**
 * Внутренняя строка текстового слоя с provenance до исходной страницы.
 *
 * @param pageNumber номер страницы
 * @param pageBlockIndex индекс исходного текстового блока на странице
 * @param lineIndex индекс строки внутри блока
 * @param order глобальный порядок строки в документе
 * @param text исходный текст строки
 */
record SourceLine(
        int pageNumber,
        int pageBlockIndex,
        int lineIndex,
        int order,
        String text
) {
}
