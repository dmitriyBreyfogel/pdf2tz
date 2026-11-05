package com.pdf2tz.backend.application.pdf.model;

import java.util.List;

/**
 * Текстовый слой PDF-документа, извлечённый из исходного файла.
 *
 * <p>Модель хранит только постраничный текст, полученный PDF-reader'ом.
 * Таблицы, изображения и другие структурные элементы документа в неё не входят
 * и обрабатываются отдельными ветками application-flow.</p>
 *
 * @param pages страницы извлечённого текстового слоя
 */
public record ExtractedTextDocument(
        List<ExtractedTextPage> pages
) {

    public ExtractedTextDocument {
        pages = List.copyOf(pages);
    }
}
