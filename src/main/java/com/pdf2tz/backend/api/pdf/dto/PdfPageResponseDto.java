package com.pdf2tz.backend.api.pdf.dto;

/**
 * DTO извлечённой страницы PDF-документа.
 *
 * @param pageNumber номер страницы в исходном PDF-документе
 * @param text извлечённый текст страницы
 */
public record PdfPageResponseDto(
        int pageNumber,
        String text
) {}
