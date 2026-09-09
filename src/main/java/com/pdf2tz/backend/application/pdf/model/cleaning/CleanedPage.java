package com.pdf2tz.backend.application.pdf.model.cleaning;

/**
 * Страница после очистки извлечённого текста.
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param text очищенный текст страницы
 */
public record CleanedPage(
        int pageNumber,
        String text
) {
}
