package com.pdf2tz.backend.application.pdf.model.cleaning;

/**
 * Страница очищенного текстового слоя PDF-документа.
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param text очищенный текст страницы
 */
public record CleanedTextPage(
        int pageNumber,
        String text
) {
}
