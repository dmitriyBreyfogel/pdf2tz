package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO результата извлечения содержимого PDF-документа.
 *
 * @param pages страницы с извлечённым текстом
 */
public record PdfResponseDto(
        List<PdfPageResponseDto> pages
) { }
