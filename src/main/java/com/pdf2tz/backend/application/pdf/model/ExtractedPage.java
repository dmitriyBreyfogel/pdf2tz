package com.pdf2tz.backend.application.pdf.model;

/**
 * Объект извлечённой страницы из PDF-документа
 * @param pageNumber номер страницы
 * @param text текст страницы
 */
public record ExtractedPage(
        int pageNumber,
        String text
) {}
