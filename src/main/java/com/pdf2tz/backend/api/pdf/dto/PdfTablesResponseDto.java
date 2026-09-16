package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO результата извлечения таблиц из PDF-документа.
 *
 * @param tables найденные таблицы документа
 */
public record PdfTablesResponseDto(
        List<PdfTableResponseDto> tables
) { }
