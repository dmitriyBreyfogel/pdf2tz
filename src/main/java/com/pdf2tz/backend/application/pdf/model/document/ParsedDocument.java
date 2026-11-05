package com.pdf2tz.backend.application.pdf.model.document;

import java.util.List;
import java.util.Objects;

/**
 * Готовый распарсенный документ для дальнейшего разбиения на чанки и передачи в LLM.
 *
 * <p>Это уже не сырой текст PDF и не промежуточный результат очистки.
 * Документ состоит из страниц, а страницы - из блоков в порядке чтения.</p>
 *
 * @param pages страницы документа
 */
public record ParsedDocument(
        List<ParsedPage> pages
) {

    public ParsedDocument {
        pages = List.copyOf(Objects.requireNonNull(pages, "Parsed document pages must not be null"));
    }
}
