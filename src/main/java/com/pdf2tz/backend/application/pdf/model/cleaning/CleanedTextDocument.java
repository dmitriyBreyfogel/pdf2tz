package com.pdf2tz.backend.application.pdf.model.cleaning;

import java.util.List;

/**
 * Очищенный текстовый слой PDF-документа.
 *
 * <p>Модель хранит только постраничный текст после удаления watermark-ов,
 * повторяющихся служебных строк и inline-шума. Структурированные таблицы
 * в эту модель не входят и соединяются с текстом позже, на этапе сборки
 * {@code ParsedDocument}.</p>
 *
 * @param pages очищенные страницы текстового слоя
 */
public record CleanedTextDocument(
        List<CleanedTextPage> pages
) {

    public CleanedTextDocument {
        pages = List.copyOf(pages);
    }
}
