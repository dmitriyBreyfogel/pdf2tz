package com.pdf2tz.backend.application.pdf.model.cleaning;

import java.util.List;

/**
 * Документ после очистки извлечённого текста от служебного шума.
 *
 * @param pages очищенные страницы документа
 */
public record CleanedDocument(
        List<CleanedPage> pages
) {

    public CleanedDocument {
        pages = List.copyOf(pages);
    }
}
