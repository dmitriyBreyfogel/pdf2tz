package com.pdf2tz.backend.application.pdf.model;

/**
 * Страница текстового слоя PDF-документа.
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param text текст страницы, полученный PDF-reader'ом
 */
public record ExtractedTextPage(
        int pageNumber,
        String text
) {}
