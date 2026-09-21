package com.pdf2tz.backend.api.pdf.dto;

/**
 * DTO одного заголовка section path.
 *
 * @param level логический уровень заголовка
 * @param text оригинальный текст заголовка
 * @param pageNumber страница заголовка
 */
public record PdfSectionHeadingResponseDto(
        int level,
        String text,
        int pageNumber
) {
}
