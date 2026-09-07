package com.pdf2tz.backend.application.pdf.model;

import java.util.List;

/**
 * Объект извлечённого документа
 * @param pages страницы документа
 */
public record ExtractedDocument (
        List<ExtractedPage> pages
) {
    public ExtractedDocument {
        pages = List.copyOf(pages);
    }
}
