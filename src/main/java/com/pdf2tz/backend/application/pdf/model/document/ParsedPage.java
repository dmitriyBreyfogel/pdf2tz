package com.pdf2tz.backend.application.pdf.model.document;

import java.util.List;
import java.util.Objects;

/**
 * Страница готового распарсенного документа.
 *
 * <p>Содержит блоки, логически относящиеся к этой странице. Страница может
 * не содержать блоков, если после очистки на ней не осталось полезного текста.</p>
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param blocks блоки страницы в порядке чтения
 */
public record ParsedPage(
        int pageNumber,
        List<DocumentBlock> blocks
) {

    public ParsedPage {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Parsed page number must be positive");
        }

        blocks = List.copyOf(Objects.requireNonNull(blocks, "Parsed page blocks must not be null"));
    }
}
